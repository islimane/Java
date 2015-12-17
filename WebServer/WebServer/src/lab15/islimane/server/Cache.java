package lab15.islimane.server;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

public class Cache {
	private final static int INTSIZE = 4;
	private final static int LONGSIZE = 8;
	
	private String path;
	private boolean debug = true;
	
	private static enum Policy {
	    FIFO, LRU, RND
	}
	private final int maxSize = 5;
	// The key is a string that represens the file path.
	private HashMap<String, DataFile> files;
	private Policy policy;
	
	public Cache(Policy policy, String path){
		this.policy = policy;
		files = new HashMap<String, DataFile>(maxSize);
		if(path.equals("/")||path.equals("."))
			this.path = "";
		else
			this.path = path;
		System.out.println("path: '" + this.path + "'");
	}
	
	/*
	 * RND policy by default
	 */
	public Cache(String path){
		this(Policy.RND, path);
	}
	
	/**
	 * This method returns true if the
	 * fileName was in the hashmap, or
	 * false if there wasn't.
	 * @param fileName
	 * @return
	 */
	public synchronized boolean del(String fileName){
		fileName = path + fileName;
		if(debug) System.out.println("fileName: '" + fileName + "'");
		DataFile d = files.remove(fileName);
		if(d==null) return false;
		return true;
	}
	
	public synchronized void changePolicy(String policy){
		if(policy.equals(Policy.RND.toString()))
			this.policy = Policy.RND;
		else if(policy.equals(Policy.FIFO.toString()))
			this.policy = Policy.FIFO;
		else if(policy.equals(Policy.LRU.toString()))
			this.policy = Policy.LRU;
		else
			System.err.println("Error: Unknown policy");
	}
	
	public synchronized byte[] getFile(String fileName){
		fileName = path + fileName;
		if(debug) System.out.println("fileName: '" + fileName + "'");
		DataFile file = findInCache(fileName);
		if(file==null){
			file = findInDisc(fileName);
			insert(file);
		}else{
			file.upadteLastAccess();
		}
		return file.getFileData();
	}
	
	/**
	 * This method look for the filename in the
	 * cache. If it does not exist, then returns
	 * null.
	 * @return
	 */
	private synchronized DataFile findInCache(String fileName){
		Set<String> keys = files.keySet();
		for(String str: keys){
			if(str.equals(fileName))
				return files.get(str);
		}
		return null;
	}
	
	/**
	 * This method returns a DataFile object. This
	 * object contains a bytes array that
	 * represents the html response for a given
	 * fileName request.
	 */
	private synchronized DataFile findInDisc(String pathName){
		File f = new File(pathName);
		byte buff[] = null;
		try{
			if(!f.exists()){
				String htmlResp = "HTTP/1.1 404 Not Found\r\n" +
						"\r\n" +
						"404 page not found";
				buff = htmlResp.getBytes(Charset.forName("UTF-8"));
			}else{
				// File found
				if(f.isDirectory()){
					File[] files = f.listFiles();
					if(files!=null){
						String dirHtmlResp = "HTTP/1.1 200 OK\r\n" +
								"Content-Type: text/html; charset=utf-8\r\n" +
								"Content-Type: text/html; charset=utf-8\r\n" +
								"\r\n" +
								"<html>\n<head>\n</head>\n<body>" +
								"<ul>";
						for(File directoryFile: files){
							dirHtmlResp += "<li>" + directoryFile + "</li>";
						}
						dirHtmlResp += "</ul>" +
								"</body>\n</html>\r\n";
						buff = dirHtmlResp.getBytes(Charset.forName("UTF-8"));
					}
				}else{
					// is not a directory
					try {
						String str = "HTTP/1.1 200 OK\r\n" +
								"\r\n";
						byte buff1[] = str.getBytes(Charset.forName("UTF-8"));
						DataInputStream dis = new DataInputStream(new FileInputStream(f));
						byte buff2[] = new byte[(int) f.length()]; 
						dis.readFully(buff2, 0, (int) f.length());
						buff = new byte[buff1.length + buff2.length];
						System.arraycopy(buff1, 0, buff, 0, buff1.length);
						System.arraycopy(buff2, 0, buff, buff1.length, buff2.length);
					} catch (Exception e) {
						System.err.println("Error: " + e);
						throw new RuntimeException(e);
					}
				}
			}
		}catch(Exception e){
			System.err.println("Error: " + e);
			throw new RuntimeException(e);
		}
		return new DataFile(pathName, buff);
	}
	
	private synchronized void insert(DataFile file){
		if(files.size()<maxSize){
			files.put(file.getFileName(), file);
		}else{
			switch(policy){
			case RND:
				insertRND(file);
				break;
			case FIFO:
				insertFIFO(file);
				break;
			case LRU:
				insertLRU(file);
				break;
			default:
				System.err.println("Error: Bad policy");
				break;
			}
		}
	}
	
	private synchronized void insertRND(DataFile file){
		int pos = randInt(0, maxSize-1);
		Set<String> keys = files.keySet();
		int i=0;
		for(String str: keys){
			if(i==pos){
				files.remove(str);
				files.put(file.getFileName(), file);
				break;
			}
			i++;
		}
	}
	
	private synchronized void insertFIFO(DataFile file){
		Set<String> keys = files.keySet();
		DataFile victim = null;
		DataFile aux = null;
		for(String str: keys){
			aux = files.get(str);
			if(victim!=null && victim.getCreationDate()>aux.getCreationDate()){
				victim = aux;
			}else if(victim==null)
				victim = aux;
		}
		files.remove(victim.getFileName());
		files.put(file.getFileName(), file);
	}
	
	private synchronized void insertLRU(DataFile file){
		Set<String> keys = files.keySet();
		DataFile victim = null;
		DataFile aux = null;
		for(String str: keys){
			aux = files.get(str);
			if(victim!=null && victim.getLastAccess()>aux.getLastAccess()){
				victim = aux;
			}else if(victim==null)
				victim = aux;
		}
		files.remove(victim.getFileName());
		files.put(file.getFileName(), file);
	}
	
	/**
	 * This method returns a random int from
	 * @min to @max.
	 * @param min
	 * @param max
	 * @return
	 */
	private synchronized static int randInt(int min, int max) {
		Random rand = new Random();
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}
	
	public synchronized int getNumOfFiles(){
		return files.size();
	}
	
	public synchronized String getPolicy(){
		return policy.toString();
	}
	
	/**
	 * This method returns the info of the
	 * pages that are contained in the
	 * cache, formated in an bytes array.
	 * The format is:
	 * name_size(int-Little_Endian)[4bytes] name(UTF-8)[name_size] file_size(int-Little_Endian)[4bytes]
	 * date(long-Little_Endian)[8bytes]
	 */
	public synchronized byte[] getformatList(){
		Set<String> keys = files.keySet();
		DataFile file = null;
		byte[] finalBuff = new byte[0];
		byte[] buff = null;
		byte[] auxBuff = null;
		for(String str: keys){
			file = files.get(str);
			try {
				byte[] fileNameSize = marshallInt(file.getFileName().length());
				byte[] fileName = file.getFileName().getBytes(Charset.forName("UTF-8"));
				byte[] size = marshallInt(file.getFileData().length);
				byte[] date = marshallLong(file.getCreationDate());
				buff = new byte[fileNameSize.length + fileName.length + size.length + date.length];
				// Create a single buff for the current file
				System.arraycopy(fileNameSize, 0, buff, 0, fileNameSize.length);
				System.arraycopy(fileName, 0, buff, fileNameSize.length, fileName.length);
				System.arraycopy(size, 0, buff, fileNameSize.length + fileName.length, size.length);
				System.arraycopy(date, 0, buff, fileNameSize.length + fileName.length + size.length, date.length);
			// Add that buffFile to the finalBuff
				auxBuff = new byte[finalBuff.length + buff.length];
				System.arraycopy(finalBuff, 0, auxBuff, 0, finalBuff.length);
				System.arraycopy(buff, 0, auxBuff, finalBuff.length, buff.length);
				finalBuff = new byte[auxBuff.length];
				System.arraycopy(auxBuff, 0, finalBuff, 0, auxBuff.length);
			} catch (Exception e) {
				System.err.println("Error: " + e);
				throw new RuntimeException(e);
			}
		}
		return finalBuff;
	}
	
	protected synchronized void flush(){
		files = new HashMap<String, DataFile>(maxSize);
	}
	
	public synchronized String toString(){
		Set<String> keys = files.keySet();
		String s = "";
		DataFile file = null;
		for(String str: keys){
			file = files.get(str);
			s += file.getFileName() + "\n";
		}
		return s;
	}
	
	/*
	 * 
	 * 
	 * MARSHALLING AND UNMARSHALLING
	 * 
	 * 
	 */
	public synchronized static byte[] marshallInt(int i) throws Exception{
		byte r[] = new byte[INTSIZE];
		r[3] = (byte) (i>>24 & 0xFF);
		r[2] = (byte) (i>>16 & 0xFF);
		r[1] = (byte) (i>>8 & 0xFF);
		r[0] = (byte) (i & 0xFF);
		return r;
	}
	
	public synchronized static int unmarshallInt(byte[] buff) throws Exception{
		ByteBuffer bb = ByteBuffer.wrap(buff);
		int n;
		if(bb.capacity()<INTSIZE)
			throw new Exception("Bad array length: must be greater or equal to " + INTSIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		n = bb.getInt();
		return n;
	}
	
	public synchronized static byte[] marshallLong(long l) throws Exception{
		byte r[] = new byte[LONGSIZE];
		r[7] = (byte) (l>>54 & 0xFF);
		r[6] = (byte) (l>>48 & 0xFF);
		r[5] = (byte) (l>>40 & 0xFF);
		r[4] = (byte) (l>>32 & 0xFF);
		r[3] = (byte) (l>>24 & 0xFF);
		r[2] = (byte) (l>>16 & 0xFF);
		r[1] = (byte) (l>>8 & 0xFF);
		r[0] = (byte) (l & 0xFF);
		return r;
	}
	
	public synchronized static long unmarshallLong(byte[] buff) throws Exception{
		ByteBuffer bb = ByteBuffer.wrap(buff);
		long n;
		if(bb.capacity()<LONGSIZE)
			throw new Exception("Bad array length: must be greater or equal to " + LONGSIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		n = bb.getLong();
		return n;
	}
}
