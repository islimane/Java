package lab15.islimane.server;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DataFile {
	// File path
	private String fileName;
	private long lastAccess;
	private long creationDate;
	private byte fileData[];
	
	public DataFile(String fileName, byte fileData[]){
		this.fileName = fileName;
		lastAccess = System.currentTimeMillis();
		creationDate = System.currentTimeMillis();
		this.fileData = fileData;
	}
	
	public DataFile(){
		lastAccess = System.currentTimeMillis();
	}

	public long getLastAccess(){
		return lastAccess;
	}
	
	public void upadteLastAccess(){
		lastAccess = System.currentTimeMillis();
	}
	
	public long getCreationDate(){
		return creationDate;
	}
	
	public String getFormatDate(){
		Date date = new Date(lastAccess);
		DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		String formatted = format.format(date);
		return formatted;
	}
	
	public byte[] getFileData(){
		return fileData;
	}
	
	public String getFileName(){
		return fileName;
	}
}
