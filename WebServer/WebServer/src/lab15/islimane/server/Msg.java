package lab15.islimane.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Msg {
	/**
	 * TNONE: null value
	 * TNAMES: tag[4] type[1]
	 * RNAMES: tag[4] type[1] ns[4] names[ns]{names are separated by ','}
	 * TPUT: tag[4] type[1] ns[4] name[ns] vs[4] value[vs]
	 * TGET: tag[4] type[1] ns[4] name[ns]
	 * RGET: tag[4] type[1] vs[4] value[vs]
	 * TDEL: tag[4] type[1] ns[4] name[vs]
	 * RERROR: tag[4] type[1] error[1]{if error is the byte 0 then no error has occurred, 1 if has occurred}
	 * 
	 * TLIST: tag[4] type[1]
	 * RLIST: tag[4] type[1] fs[4] files[ns]{names are separated by ','}
	 * 
	 */
	private static final byte TNONE = 0;
	private static final byte TFLUSH = 66;
	private static final byte TDEL = 68;
	private static final byte RDEL = 69;
	private static final byte TLIST = 70;
	private static final byte RLIST = 71;
	private static final byte TPOL = 72;
	private static final byte RPOL = 73;
	private static final byte TQUIT = 74;
	private static final byte RERROR = 75;
	
	private final static int INTSIZE = 4;
	private final static int LONGSIZE = 8;
	
	private static int currentTag;

	private int tag;
	private final byte type;
	
	public Msg(int tag, byte type){
		this.tag = tag;
		this.type = type;
	}
	
	protected String getType(byte type){
		switch(type){
		case TNONE: return "TNONE";
		case TFLUSH: return "TFLUSH";
		case TDEL: return "TDEL";
		case RDEL: return "RDEL";
		case TLIST: return "TLIST";
		case RLIST: return "RLIST";
		case TPOL: return "TPOL";
		case RPOL: return "RPOL";
		case TQUIT: return "TQUIT";
		case RERROR: return "RERROR";
		default: throw new RuntimeException("Bad message type");
		}
	}
	
	public String toString(){
		return getType(type) + "[" + tag + "]";
	}
	
	public void writeTo(DataOutputStream dos){
		try {
			writeInt(dos, tag);
			dos.writeByte(type);
		} catch (IOException e) {
			System.err.println("writeTo: " + e);
			throw new RuntimeException(e);
		}
	}
	
	public static Msg readFrom(DataInputStream dis){
		int tag = 0;
		tag = readInt(dis);
		try {
			byte type = dis.readByte();
			switch(type){
			case TNONE: throw new RuntimeException("Recived TNONE");
			case TLIST: return new TList(tag, dis);
			case RLIST: return new RList(tag, dis);
			case TFLUSH: return new TFlush(tag, dis);
			case TPOL: return new TPol(tag, dis);
			case RPOL: return new RPol(tag, dis);
			case TDEL: return new TDel(tag, dis);
			case RDEL: return new RDel(tag, dis);
			case TQUIT: return new TQuit(tag, dis);
			default: throw new RuntimeException("Bad message type");
			}
		} catch (Exception e) {
			System.err.println("readFrom: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected static int readInt(DataInputStream dis){
		byte[] buff = new byte[INTSIZE];
		try {
			dis.read(buff, 0, INTSIZE);
			return unmarshallInt(buff);
		} catch (Exception e) {
			System.err.println("readInt: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected static void writeInt(DataOutputStream dos, int i){
		try {
			byte[] buff = marshallInt(i);
			dos.write(buff, 0, buff.length);
		} catch (Exception e) {
			System.err.println("writeInt: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected static long readLong(DataInputStream dis){
		byte[] buff = new byte[LONGSIZE];
		try {
			dis.read(buff, 0, LONGSIZE);
			return unmarshallLong(buff);
		} catch (Exception e) {
			System.err.println("readLong: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected static void writeLong(DataOutputStream dos, long l){
		try {
			byte[] buff = marshallLong(l);
			writeBytes(dos, buff);
		} catch (Exception e) {
			System.err.println("writeLong: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected String readString(DataInputStream dis){
		byte[] buff = readBytes(dis);
		try {
			String s = new String(buff, "UTF-8");
			return s;
		} catch (Exception e) {
			System.err.println("readString: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected void writeString(DataOutputStream dos, String s){
		try {
			byte[] buff = s.getBytes("UTF-8");
			writeBytes(dos, buff);
		} catch (Exception e) {
			System.err.println("writeString: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected static byte[] readBytes(DataInputStream dis){
		try {
			int n = readInt(dis);
			byte[] buff = new byte[n];
			dis.readFully(buff);
			return buff;
		} catch (Exception e) {
			System.err.println("readBytes: " + e);
			throw new RuntimeException(e);
		}
	}
	
	protected static void writeBytes(DataOutputStream dos, byte[] buff){
		int n = buff.length;
		try {
			// This int indicates the buff size to read
			writeInt(dos, n);
			// bytes
			dos.write(buff);
		} catch (IOException e) {
			System.err.println("writeBytes: " + e);
			throw new RuntimeException(e);
		}
	}
	
	private static int getTag(){
		currentTag++;
		return currentTag;
	}
	
	/*
	 * 
	 * LIST
	 * 
	 */
	public static class TList extends Msg{
		public TList(){
			super(getTag(), TLIST);
		}
		
		public TList(int tag, DataInputStream dis){
			super(tag, TLIST);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[TLIST]";
		}
	}
	
	/**
	 * RList format: tag[4] type[1] numOfFiles[4] buffSize[4] filesBuff[buffSize]
	 */
	public static class RList extends Msg{
		private int numOfFiles;
		private byte[] files;
		
		public RList(byte[] files, int numOfFiles){
			super(getTag(), RLIST);
			this.files = files;
			this.numOfFiles = numOfFiles;
		}
		
		public RList(int tag, DataInputStream dis){
			super(tag, RLIST);
			numOfFiles = readInt(dis);
			int buffSize = readInt(dis);
			files = new byte[buffSize];
			try {
				dis.read(files, 0, buffSize);
			} catch (IOException e) {
				System.err.println("writeBytes: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				writeInt(dos, numOfFiles);
				writeInt(dos, files.length);
				dos.write(files, 0, files.length);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public byte[] getFiles(){
			return files;
		}
		
		public int getNumOfFiles(){
			return numOfFiles;
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[RLIST]" +
					" numOfFiles[" + numOfFiles + "] fs[" + files.length + "] files[" + files + "]";
		}
	}
	
	/*
	 * 
	 * FLUSH
	 * 
	 */
	/**
	 * TFlush format: tag[4] type[1]
	 */
	public static class TFlush extends Msg{
		public TFlush(){
			super(getTag(), TFLUSH);
		}
		
		public TFlush(int tag, DataInputStream dis){
			super(tag, TFLUSH);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[TFLUSH]";
		}
	}
	
	/*
	 * 
	 * POL
	 * 
	 */
	/**
	 * TPol format: tag[4] type[1] policy[polSize]
	 */
	public static class TPol extends Msg{
		private String policy;
		
		public TPol(String policy){
			super(getTag(), TPOL);
			this.policy = policy;
		}
		
		public TPol(int tag, DataInputStream dis){
			super(tag, TPOL);
			policy = readString(dis);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				writeString(dos, policy);
				dos.flush();
		} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String getPolicy(){
			return policy;
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[TPOL] ps[4] policy[" + policy + "]";
		}
	}
	
	/**
	 * RPol format: tag[4] type[1] policy[polSize]
	 */
	public static class RPol extends Msg{
		private String policy;
		
		public RPol(String policy){
			super(getTag(), RPOL);
			this.policy = policy;
		}
		
		public RPol(int tag, DataInputStream dis){
			super(tag, RPOL);
			policy = readString(dis);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				writeString(dos, policy);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String getPolicy(){
			return policy;
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[RPOL] ps[4] policy[" + policy + "]";
		}
	}
	
	/*
	 * 
	 * DEL
	 * 
	 */
	/**
	 * TDel format: tag[4] type[1] fns[4] fileName[fns]
	 */
	public static class TDel extends Msg{
		private String fileName;
		
		public TDel(String fileName){
			super(getTag(), TDEL);
			this.fileName = fileName;
		}
		
		public TDel(int tag, DataInputStream dis){
			super(tag, TDEL);
			fileName = readString(dis);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				writeString(dos, fileName);
				dos.flush();
		} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String getFileName(){
			return fileName;
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[TDEL] fns[4] fileName[" + fileName + "]";
		}
	}
	
	/**
	 * RDel format: tag[4] type[1] fs[4] found[1]
	 */
	public static class RDel extends Msg{
		// found = null if not found, found="found" if found.
		private String found;
		
		public RDel(String found){
			super(getTag(), RDEL);
			this.found = found;
		}
		
		public RDel(int tag, DataInputStream dis){
			super(tag, RDEL);
			found = readString(dis);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				writeString(dos, found);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String found(){
			return found;
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[RDEL] fs[4] found[" + found + "]";
		}
	}
	
	public static class RError extends Msg{
		private byte error;
		
		public RError(byte error){
			super(getTag(), RERROR);
			this.error = error;
		}
		
		public RError(int tag, DataInputStream dis){
			super(tag, RERROR);
			error = readBytes(dis)[0];
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				byte[] buff = new byte[1];
				buff[0] = error;
				writeBytes(dos, buff);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public byte getError(){
			return error;
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[RERROR] error[" + error + "]";
		}
	}
	
	/*
	 * 
	 * QUIT
	 * 
	 */
	/**
	 * TQuit format: tag[4] type[1]
	 */
	public static class TQuit extends Msg{
		public TQuit(){
			super(getTag(), TQUIT);
		}
		
		public TQuit(int tag, DataInputStream dis){
			super(tag, TQUIT);
		}
		
		public void writeTo(DataOutputStream dos){
			try {
				super.writeTo(dos);
				dos.flush();
			} catch (IOException e) {
				System.err.println("writeTo: IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		public String toString(){
			return "tag[" + super.tag + "] type[TQUIT]";
		}
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
}
