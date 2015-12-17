package lab15.islimane.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import lab15.islimane.server.Msg;

public class CtrlClient {
	private final int srvPort = 9090;
	private String hostName;
	private Socket sk;
	protected DataOutputStream dos;
	private DataInputStream dis;
	private boolean debug = false;
	
	private final static int INTSIZE = 4;
	private final static int LONGSIZE = 8;
	
	public CtrlClient(String name){
		this.hostName = name;
	}
	
	/**
	 * This method close all
	 * the streams
	 */
	public void close(){
		try {
			if(dos!=null)
				dos.close();
			if(dis!=null)
			dis.close();
		} catch (IOException e) {
			// Don't care
		}
	}
	
	public void connect(){
		try {
			sk = new Socket(hostName, srvPort);
			dos = new DataOutputStream(sk.getOutputStream());
			dis = new DataInputStream(sk.getInputStream());
		} catch (Exception e) {
			System.err.println("connect: " + e);
			throw new RuntimeException(e);
		}
	}

	public void ls() {
		Msg send = new Msg.TList();
		if(debug) System.err.println("[cli]: sent: " + send);
		send.writeTo(dos);
		Msg rcv =  Msg.readFrom(dis);
		if(debug) System.err.println("[cli]: rcv: " + rcv);
		String files[] = process(rcv);
		for(String str: files){
			System.out.println(str);
		}
	}
	
	public void flush() {
		Msg send = new Msg.TFlush();
		if(debug) System.err.println("[cli]: sent: " + send);
		send.writeTo(dos);
		System.out.println("ok");
	}
	
	public void policy(String policy) {
		Msg send = new Msg.TPol(policy);
		if(debug) System.err.println("[cli]: sent: " + send);
		send.writeTo(dos);
		Msg rcv =  Msg.readFrom(dis);
		if(debug) System.err.println("[cli]: rcv: " + rcv);
		String args[] = process(rcv);
		if(args.length!=1)
			System.err.println("Error: couldn't change policy");
		else
			System.out.println("changed policy from " + args[0] + " to " + policy);
	}
	
	public void del(String fileName) {
		Msg send = new Msg.TDel(fileName);
		if(debug) System.err.println("[cli]: sent: " + send);
		send.writeTo(dos);
		Msg rcv =  Msg.readFrom(dis);
		if(debug) System.err.println("[cli]: rcv: " + rcv);
		String args[] = process(rcv);
		if(args==null)
			System.out.println("not found");
		else
			System.out.println("ok");
	}
	
	public void quit() {
		Msg send = new Msg.TQuit();
		if(debug) System.err.println("[cli]: sent: " + send);
		send.writeTo(dos);
		System.out.println("ok");
	}
	
	/**
	 * This method calls the
	 * process method for
	 * each type
	 */
	private String[] process(Msg msg){
		if(msg instanceof Msg.RList)
			return procRList(msg);
		else if(msg instanceof Msg.RPol)
			return procRPol(msg);
		else if(msg instanceof Msg.RDel)
			return procRDel(msg);
		else if(msg instanceof Msg.RError)
			procRError(msg);
		else
			System.err.println("[cli]: Error: bad message type");
		return null;
	}
	
	private String[] procRDel(Msg msg){
		String args[] = new String[1];
		Msg.RDel msgRDel= (Msg.RDel) msg;
		if(msgRDel.found().equals("found")){
			args[0] = "found";
			return args;
		}
		return null;
	}
	
	private String[] procRPol(Msg msg){
		Msg.RPol msgRPol= (Msg.RPol) msg;
		String policy[] = new String[1];
		policy[0] = msgRPol.getPolicy();
		return policy;
	}
	
	private String[] procRList(Msg msg){
		Msg.RList msgRList = (Msg.RList) msg;
		byte[] files = msgRList.getFiles();
		int numOfFiles = msgRList.getNumOfFiles();
		String[] filesInfoArr = new String[numOfFiles];
		byte[] fileNameSize = null;
		byte[] fileName = null;
		byte[] fileSize = null;
		byte[] date = null;
		int totSize = 0;
		for(int i=0;i<numOfFiles;i++){
			try {
				// Get the file name size
				fileNameSize = new byte[INTSIZE];
				System.arraycopy(files, totSize, fileNameSize, 0, INTSIZE);
				totSize += fileNameSize.length;
				// Get the file name
				fileName = new byte[unmarshallInt(fileNameSize)];
				System.arraycopy(files, totSize, fileName, 0, unmarshallInt(fileNameSize));
				totSize += fileName.length;
				// Get the file size
				fileSize = new byte[INTSIZE];
				System.arraycopy(files, totSize, fileSize, 0, INTSIZE);
				totSize += fileSize.length;
				// Get date
				date = new byte[LONGSIZE];
				System.arraycopy(files, totSize, date, 0, LONGSIZE);
				totSize += date.length;
				// Create the file info string and insert it into the filesInfoArr
				String fileInfo;
				fileInfo = (new String(fileName, "UTF-8")) + "\t" +
							unmarshallInt(fileSize) + " bytes " + getFormatDate(unmarshallLong(date));
				filesInfoArr[i] = fileInfo;
			} catch (Exception e) {
				System.err.println("connect: " + e);
				throw new RuntimeException(e);
			}
		}
		return filesInfoArr;
	}
	
	/*private String[] procRGet(Msg msg){
		Msg.RGet msgRGet = (Msg.RGet) msg;
		String[]  strArr = new String [1];
		strArr[0] =  msgRGet.getValue();
		return strArr;
	}*/
	
	private String procRError(Msg msg){
		Msg.RError msgError = (Msg.RError) msg;
		if(msgError.getError()== (byte) 1)
			System.err.println("Error: couldn't del");
		return "";
	}
	
	public String getFormatDate(long epochDate){
		Date date = new Date(epochDate);
		DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		String formatted = format.format(date);
		return formatted;
	}
	
	/*
	 * 
	 * 
	 * MARSHALLING AND UNMARSHALLING
	 * 
	 * 
	 */
	
	public static byte[] marshallInt(int i) throws Exception{
		byte r[] = new byte[INTSIZE];
		r[3] = (byte) (i>>24 & 0xFF);
		r[2] = (byte) (i>>16 & 0xFF);
		r[1] = (byte) (i>>8 & 0xFF);
		r[0] = (byte) (i & 0xFF);
		return r;
	}
	
	public static int unmarshallInt(byte[] buff) throws Exception{
		ByteBuffer bb = ByteBuffer.wrap(buff);
		int n;
		if(bb.capacity()<INTSIZE)
			throw new Exception("Bad array length: must be greater or equal to " + INTSIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		n = bb.getInt();
		return n;
	}
	
	public static byte[] marshallLong(long l) throws Exception{
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
	
	public static long unmarshallLong(byte[] buff) throws Exception{
		ByteBuffer bb = ByteBuffer.wrap(buff);
		long n;
		if(bb.capacity()<LONGSIZE)
			throw new Exception("Bad array length: must be greater or equal to " + LONGSIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		n = bb.getLong();
		return n;
	}
	
	/*
	 * 
	 * 
	 * MAIN
	 * 
	 * 
	 */
	
	public static void main(String args[]) {
		if(args.length<1)
			System.err.println("Usage: CtrlClient [option]");
		else{
			CtrlClient cc = new CtrlClient("localhost");
			cc.connect();
			if(args[0].equals("ls") && args.length==1)
				cc.ls();
			else if(args[0].equals("flush") && args.length==1)
				cc.flush();
			else if(args[0].equals("policy") && args.length==2)
				cc.policy(args[1]);
			else if(args[0].equals("del") && args.length==2)
				cc.del(args[1]);
			else if(args[0].equals("quit") && args.length==1)
				cc.quit();
			else
				System.err.println("Usage: bad usage");
			cc.close();
		}
			
	}
}
