package lab15.islimane.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class WebSrv {
	private ServerSocket webSsk;
	private ServerSocket ctrlSsk;
	private final int mainPort = 8181;
	private final int ctrlPort = 9090;
	private Cache cache;
	private boolean exit = false;
	private boolean debug = false;

	public WebSrv(String path){
		try {
			webSsk = new ServerSocket(mainPort);
			ctrlSsk = new ServerSocket(ctrlPort);
			cache = new Cache(path);
		} catch (IOException e) {
			System.err.println("IOException: " + e);
			throw new RuntimeException(e);
		}
	}
	
	public void start(){
		new Thread() {
			public void run() {
				serveWeb(webSsk);
			}
		}.start();
		new Thread() {
			public void run() {
				serveCtrl(ctrlSsk);
			}
		}.start();
	}
	
	private void serveWeb(ServerSocket ssk){
		Socket cli;
		BufferedReader br;
		DataOutputStream dos;
		while(!exit){
			try {
				cli = ssk.accept();
				br = new BufferedReader(new InputStreamReader(cli.getInputStream()));
				dos = new DataOutputStream(new BufferedOutputStream(cli.getOutputStream()));
				WebCli wc = new WebCli(cli, br, dos);
				new Thread(new SrvWebCli(wc)).start();
			} catch (IOException e) {
				if(exit){
					// quit was called
				}else{
					System.err.println("IOException: " + e);
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	private void close(){
		try {
			exit = true;
			webSsk.close();
			ctrlSsk.close();
		} catch (Exception e) {
			//
		}
	}
	
	private void serveCtrl(ServerSocket ssk){
		Socket cli;
		DataInputStream dis;
		DataOutputStream dos;
		while(!exit){
			try {
				cli = ssk.accept();
				dis = new DataInputStream(new BufferedInputStream(cli.getInputStream()));
				dos = new DataOutputStream(new BufferedOutputStream(cli.getOutputStream()));
				CtrlCli cc = new CtrlCli(cli, dis, dos);
				new Thread(new SrvCtrlCli(cc, this)).start();
			} catch (IOException e) {
				if(exit){
					// quit was called
				}else{
					System.err.println("IOException: " + e);
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	private static class WebCli{
		private Socket cliSk;
		private BufferedReader br;
		private DataOutputStream dos;
		
		public WebCli(Socket cliSk, BufferedReader br, DataOutputStream dos){
			this.cliSk = cliSk;
			this.dos = dos;
			this.br = br;
		}
		
		/**
		 * This method close all
		 * the streams and the
		 * client socket
		 */
		public synchronized void close(){
			try {
				dos.close();
				br.close();
				cliSk.close();
			} catch (IOException e) {
				// Don't care
			}
		}
	}
	
	private class CtrlCli{
		private Socket cliSk;
		private DataOutputStream dos;
		private DataInputStream dis;
		
		public CtrlCli(Socket cliSk, DataInputStream dis, DataOutputStream dos){
			this.cliSk = cliSk;
			this.dos = dos;
			this.dis = dis;
		}
		
		public DataInputStream getDis(){
			return dis;
		}
		
		public DataOutputStream getDos(){
			return dos;
		}
		
		/**
		 * This method close all
		 * the streams and the
		 * client socket
		 */
		public synchronized void close(){
			try {
				dis.close();
				dos.close();
				cliSk.close();
			} catch (IOException e) {
				// Don't care
			}
		}
	}
	
	private class SrvWebCli implements Runnable{
		WebCli cli;
		BufferedReader br;
		DataOutputStream dos;
		
		public SrvWebCli(WebCli wc){
			cli = wc;
			this.br = wc.br;
			this.dos = wc.dos;
		}
		
		public void run() {
			serveCli(br, dos);
			cli.close();
		}
		
		private void serveCli(BufferedReader br, DataOutputStream dos){
			String line;
			try {
				line = br.readLine();
				if(debug) System.err.println("[srv]: rcv: line: '" + line + "'");
				String fileName = procReq(line);
				if(fileName != null){
					if(debug) System.err.println("[srv]: rcv: request for: '" + procReq(line) + "'");
					br.readLine();
					byte buff[];
					synchronized(cache){
						buff = cache.getFile(fileName);
					}
					dos.write(buff, 0, buff.length);
					dos.flush();
				}
			} catch (IOException e) {
				System.err.println("IOException: " + e);
				throw new RuntimeException(e);
			}
		}
		
		private String procReq(String req){
			if(req==null)
				return null;
			String[] reqArr = req.split(" ");
			if(reqArr.length==3){
				if(reqArr[0].equals("GET") && reqArr[2].equals("HTTP/1.1") && reqArr[1].charAt(0)=='/'){
					return reqArr[1];
				}
			}
			System.err.println("Error: Unknown Protocol");
			return null;
		}
	}
	
	private class SrvCtrlCli implements Runnable{
		private CtrlCli cli;
		private DataInputStream dis;
		private DataOutputStream dos;
		private WebSrv srv;
		
		public SrvCtrlCli(DataInputStream dis, DataOutputStream dos){
			this.dis = dis;
			this.dos = dos;
		}
		
		public SrvCtrlCli(CtrlCli cc, WebSrv srv){
			this(cc.getDis(), cc.getDos());
			this.cli = cc;
			this.srv = srv;
		}
		
		public void run() {
			serveCli(dis, dos);
			close();
		}
		
		private void serveCli(DataInputStream dis, DataOutputStream dos){
			Msg msg = Msg.readFrom(dis);
			if(debug) System.err.println("[srv]: " + msg);
			if(msg!=null){
				Msg response = process(msg);
				if(debug) System.err.println("[srv]: sent: " + response);
				if(response!=null) response.writeTo(dos);
			}
			cli.close();
		}
		
		/**
		 * This method calls the
		 * process method for
		 * each type and returns the
		 * msg response
		 */
		private Msg process(Msg msg){
			if(msg instanceof Msg.TList)
				return procTList();
			else if(msg instanceof Msg.TFlush)
				procTFlush();
			else if(msg instanceof Msg.TPol)
				return procTPol(msg);
			else if(msg instanceof Msg.TDel)
				return procTDel(msg);
			else if(msg instanceof Msg.TQuit)
				procTQuit();
			
			
				return null;
		}
		
		private Msg procTDel(Msg msg){
			Msg.TDel msgTDel = (Msg.TDel) msg;
			String found = "found";
			if(cache.del(msgTDel.getFileName())){
				return new Msg.RDel(found);
			}
			found = "not found";
			return new Msg.RDel(found);
		}
		
		private Msg procTList(){
			byte[] files = cache.getformatList();
			return new Msg.RList(files, cache.getNumOfFiles());
		}
		
		private Msg procTPol(Msg msg){
			String policy = cache.getPolicy();
			Msg.TPol msgTPol = (Msg.TPol) msg;
			cache.changePolicy(msgTPol.getPolicy());
			return new Msg.RPol(policy);
		}
		
		private void procTFlush(){
			cache.flush();
		}
		
		private void procTQuit(){
			srv.close();
		}
		
		private void close(){
			try {
				dis.close();
				dos.close();
			} catch (IOException e) {
				// Don't care
			}
		}
	}
	
	public static void main(String args[]) {
		if(args.length!=1)
			System.err.println("Usage: WebSrv [path]");
		WebSrv srv = new WebSrv(args[0]);
		srv.start();
	}
}
