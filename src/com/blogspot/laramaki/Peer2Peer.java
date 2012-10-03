package com.blogspot.laramaki;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.util.ByteArrayBuffer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class Peer2Peer {

	public static final int					TIPO_OBJETO_IMAGEM	= 1;
	public static final int					TIPO_OBJETO_TEXTO	= 2;

	public List<String>						peers				= new ArrayList<String>();
	private Context							context;
	private String							ipLocal;
	private Drawable						objetoDrawable;
	private ListenerDeNovosObjetosRecebidos	listenerDeNovosObjetosRecebidos;

	public Peer2Peer(Context context) {
		this.context = context;
		new Thread(new Runnable() {

			public void run() {
				try {
					getEnderecoLocal();
					iniciaServidor();
				}
				catch (SocketException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void setListenerDeNovosObjetosRecebidos(ListenerDeNovosObjetosRecebidos listenerDeNovosObjetos) {
		this.listenerDeNovosObjetosRecebidos = listenerDeNovosObjetos;
	}

	private void getEnderecoLocal() throws SocketException {
		Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
		for (; n.hasMoreElements();) {
			NetworkInterface e = n.nextElement();
			Enumeration<InetAddress> a = e.getInetAddresses();
			for (; a.hasMoreElements();) {
				InetAddress addr = a.nextElement();
				if (!addr.isLoopbackAddress() && InetAddressUtils.isIPv4Address(addr.getHostAddress())) {
					ipLocal = addr.getHostAddress();
				}
			}
		}
	}

	private void iniciaServidor() throws SocketException {
		byte[] buffer = new byte[1024];
		int port = 5000;
		String message;
		DatagramSocket socket = new DatagramSocket(port);

		while (true) {

			try {
				// android.util.Log.i("Peer2Peer", "Server is started");
				System.out.println("Server is started");
				// Receive request from client
				DatagramPacket packet = new DatagramPacket(new byte[8192], 8192);
				socket.receive(packet);
				InetAddress client = packet.getAddress();
				int client_port = packet.getPort();
				String resposta = new String(packet.getData()).trim();
				System.out.println("Received " + resposta + " from " + client);

				// protocolo
				if (resposta.startsWith("M]")) {
					resposta = resposta.substring(2);
					final String r = resposta;
					((Activity) context).runOnUiThread(new Runnable() {
						public void run() {
							android.widget.Toast.makeText(context, "" + r, android.widget.Toast.LENGTH_LONG).show();
						}
					});
					// Imagem
				}
				else {//if (resposta.startsWith("I]")) {
//					resposta = resposta.substring(2);
					setObjeto(TIPO_OBJETO_IMAGEM, packet.getData());
				}

				// send information to the client
				message = "Yes, i'm alive";
				buffer = message.getBytes();
				packet = new DatagramPacket(buffer, buffer.length, client, client_port);
				socket.send(packet);
				System.out.println("Pacote enviado para " + client + " " + client_port);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void setObjeto(int tipo, byte[] data) {
		if (listenerDeNovosObjetosRecebidos != null) {
			switch (tipo) {
				case TIPO_OBJETO_IMAGEM:
					objetoDrawable = new BitmapDrawable(BitmapFactory.decodeByteArray(data, 0, data.length));
					listenerDeNovosObjetosRecebidos.objetoRecebido(null, TIPO_OBJETO_IMAGEM, objetoDrawable);
					break;
				case TIPO_OBJETO_TEXTO:
					listenerDeNovosObjetosRecebidos.objetoRecebido(null, TIPO_OBJETO_TEXTO, new String(data).trim());
					break;
			}
		}
	}

	private void procuraPeers() {
		String mensagem = "B]";
		byte[] buffer = mensagem.getBytes();
		int port = 5000;
		try {
			InetAddress address = InetAddress.getByName("255.255.255.255");
			DatagramSocket socketUDP = new DatagramSocket();
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
			socketUDP.send(packet);

			DatagramPacket packet2 = new DatagramPacket(buffer, buffer.length);
			socketUDP.setSoTimeout(15000);
			for (int i = 0; i < 3; i++) {
				socketUDP.receive(packet2);

				address = packet2.getAddress();
				if (!address.getHostAddress().equals(ipLocal) && !peers.contains(address.getHostAddress())) {
					peers.add(address.getHostAddress());
				}
			}
			socketUDP.close();
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
		}
		catch (SocketException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void enviaMensagemDeTexto(final String mensagem) {
		new Thread(new Runnable() {

			public void run() {
				procuraPeers();
				int port = 5000;
				String message = "M]" + mensagem;
				try {
					DatagramSocket socket = new DatagramSocket();
					for (String address : peers) {
						InetAddress inetAddress = InetAddress.getByName(address);
						byte[] msg = new byte[1024];
						msg = message.getBytes();
						android.util.Log.i("Peer2Peer", "######## " + new String(msg));
						DatagramPacket packet = new DatagramPacket(msg, msg.length, inetAddress, port);
						socket.send(packet);
					}
					socket.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void enviaImagem(final Drawable drawable) {
		new Thread(new Runnable() {

			public void run() {
				procuraPeers();
				Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
				byte[] bitmapdata = stream.toByteArray();
				ByteArrayBuffer buffer = new ByteArrayBuffer(bitmapdata.length + 2);
				//TODO Melhorar esse codigo, repetitivo. Definir na classe Protocolo. Criar constantes
				buffer.append("I]".getBytes(), 0, "I]".getBytes().length);
				buffer.append(bitmapdata, 0, bitmapdata.length);
				int port = 5000;
				try {
					DatagramSocket socket = new DatagramSocket();
					for (String address : peers) {
						InetAddress inetAddress = InetAddress.getByName(address);
						DatagramPacket packet = new DatagramPacket(bitmapdata, bitmapdata.length, inetAddress, port);
						socket.send(packet);
						android.util.Log.i("Peer2Peer", "Imagem Enviada");
					}
					socket.close();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private byte[] concatenarArrays(byte[] a, byte[] b) {
		if (a == null)
			return b;
		if (b == null)
			return a;
		byte[] r = new byte[a.length + b.length];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}

	public List<String> getListaDePeers() {
		return this.peers;
	}

	public String getMeuEnderecoIP() {
		return this.ipLocal;
	}

	public interface ListenerDeNovosObjetosRecebidos {
		public void objetoRecebido(String endereco, int tipo, Object objeto);
	}
}
