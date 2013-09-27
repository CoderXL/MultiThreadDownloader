package com.example.net.download;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.service.FileService;

import android.R.integer;
import android.content.Context;
import android.util.Log;

public class FileDownLoader {
	private Context context;
	private File saveFile;//���ر����ļ�
	private String downloadurl;//����·��
	private int fileSize=0;//�ļ���С
	private int downloadSize=0;//�Ѿ����ص��ļ���С
	private FileService fileService; //���ؼ�¼������ҵ�����
	private DownloadThread[]  threads;
	private int block;//ÿ���߳����ص����ݳ���
	private Map<Integer, Integer> data=new ConcurrentHashMap<Integer, Integer>();//������߳����صĳ���
	

	/**  
	 * <b>����������</b>	<br/>  
	 * @param args   
	 * @exception	<br/> 
	 * @since  1.0.0  
	 */
	public FileDownLoader(Context context,String downloadurl,File savedirFile,int thredNum){
		try {
			this.downloadurl=downloadurl;
			URL url=new URL(downloadurl);
			threads=new DownloadThread[thredNum];
			this.context=context;
			HttpURLConnection connection=(HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(5000);
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "image/gif, image/jpeg, image/pjpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
			connection.setRequestProperty("Accept-Language", "zh-CN");
			connection.setRequestProperty("Charset", "UTF-8");
			connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
			connection.setRequestProperty("Connection", "Keep-Alive");
			connection.connect();
			if(connection.getResponseCode()==200){
				fileSize=connection.getContentLength();
				block=(fileSize % threads.length)==0?fileSize / threads.length:fileSize/threads.length+1;//����ÿ���߳����ص����ݳ���
				String fileName=downloadurl.substring(downloadurl.lastIndexOf("/")+1);//����ļ���
				this.saveFile=new File(savedirFile, fileName);
				fileService=new FileService(context);
				Map<Integer,Integer> logdata=fileService.getData(downloadurl);
				if(logdata.size()>0){//����������ؼ�¼
					for(Map.Entry<Integer, Integer> entry: logdata.entrySet()){
						data.put(entry.getKey(), entry.getValue());//�Ѹ����߳��Ѿ����ص����ݳ��ȷ���data�С�
					}
				}
				if(data.size()==threads.length){//���������߳��Ѿ����ص������ܳ���
					for(int i=0;i<threads.length;i++){
						downloadSize+=data.get(i+1);
					}
					Log.i("downloadService","�Ѿ����ص����ݳ���"+downloadSize);
				}
				
				
			}
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * ��ʼ�����ļ�
	 * <b>����������</b>	<br/>  
	 * @param listner
	 * @return   
	 * @exception	<br/> 
	 * @since  1.0.0
	 */
	public int download(DownloadProgressListner listner){
		try{
			RandomAccessFile accessFile=new RandomAccessFile(saveFile, "rw");
			if(fileSize>0){
				accessFile.setLength(fileSize);
			}
			accessFile.close();
			URL url=new URL(downloadurl);
			if(data.size()!=threads.length){//���ԭ�����ص��߳������������ص��߳�����һ��
				this.data.clear();//��ԭ����map�������
				for(int i=0;i<threads.length;i++){
					data.put(i+1, 0);//��ʼ��ÿ���߳��Ѿ����ص����ݳ���Ϊ0
				}
				downloadSize=0;
			}
			for(int i=0;i<threads.length;i++){//�����߳̽�������
				int downloadlength=this.data.get(i+1);//����ǰ�ĵ�i���̸߳����س��ȡ�
				if(downloadlength < block && downloadSize < fileSize){//�ж��߳��Ƿ���������أ������������
					this.threads[i]=new DownloadThread(this, block, saveFile, i+1, url, this.data.get(i+1));//�߳�Id��1��ʼ
					this.threads[i].setPriority(7);
					this.threads[i].start();
				}else{
					this.threads[i]=null;
				}
			}
			fileService.delete(downloadurl);
			fileService.save(downloadurl, data);
			boolean notFinish=true;//����δ��ɡ�
			while(notFinish){
				Thread.sleep(900);
				for(int i=0;i<threads.length;i++){
					if(this.threads[i]!=null && !this.threads[i].isfinish()){
						notFinish=true;
						if(threads[i].getDownloadLength()==-1){//�������ʧ�ܣ����������ء�
							this.threads[i]=new DownloadThread(this, block, saveFile, i+1, url, this.data.get(i+1));
							this.threads[i].setPriority(7);
							this.threads[i].start();
						}
						
					}
				}
				if(listner!=null){
					listner.onDownloadSize(downloadSize);
				}
				
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		return downloadSize;
	}
	/**
	 * ����ָ���߳�������ص�λ�� ����ǰҪ����synchronized
	 * <b>����������</b>	<br/>  
	 * @param threadId
	 * @param downlength   
	 * @exception	<br/> 
	 * @since  1.0.0
	 */
	public synchronized  void update(int threadId,int downlength){
		this.data.put(threadId, downlength);
		fileService.update(this.downloadurl, threadId, downlength);
	}
	/**
	 * �ۼ��Ѿ����ش�С
	 * <b>����������</b>	<br/>  
	 * @param size   
	 * @exception	<br/> 
	 * @since  1.0.0
	 */
	public synchronized void  append(int size){
		this.downloadSize+=size;
	}

}
