import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Finate State Machine (FSM) Java Example: Woman
 * (lecture Slides for first lecture, p. 19)
 */


/**
 * Class which models the state machine itself.
 *
 */
public class FileReceiver implements Runnable {
	private boolean onceThru = false;

	private DatagramSocket receivingSocket;
	
	// all states for this FSM
	enum State {
		WAIT0, WAIT1
	};
	// all messages/conditions which can occur
	enum Msg {
		RCV_NOTCORRUPT_SEQ0, RCV_NOTCORRUPT_SEQ1, RESEND_ACK0, RESEND_ACK1
	}
	// current state of the FSM	
	private State currentState;
	// 2D array defining all transitions that can occur
	private Transition[][] transition;
	//
	private FileOutputStream file;
	// First Packet
	private boolean firstPkt = true;
	// Number of Pkts which FSM receives.
	private int numberOfpkts;
	// Received Packet
	DatagramPacket rcvpkt= new DatagramPacket(new byte[1400],1400);
	// End of File reached
	private boolean endOfFile = false;
	// ACK0 ByteArray
	private final static byte[] ACK0 = FileSenderFSM.genACK.apply(0);
	// ACK1 ByteArray
	private final static byte[] ACK1 = FileSenderFSM.genACK.apply(1);

	/**
	 * constructor
	 */

	public FileReceiver(int receivingPort) throws FileNotFoundException, SocketException {
		currentState = State.WAIT0;

		file = new FileOutputStream("Recieved_File.txt");

		receivingSocket = new DatagramSocket(receivingPort);

		// define all valid state transitions for our state machine
		// (undefined transitions will be ignored)
		transition = new Transition[State.values().length] [Msg.values().length];

		// transition[State.WAIT_FOR_CONNECTION.ordinal()] [Msg.INCOMMING_CONNECTION.ordinal()] = new EstablishConnection(); // unnötig

		transition[State.WAIT0.ordinal()] [Msg.RCV_NOTCORRUPT_SEQ0.ordinal()] = new ProcessPacket(); 		// WAIT0 -> WAIT1
		transition[State.WAIT0.ordinal()] [Msg.RESEND_ACK1.ordinal()] = new ResendAck();					// WAIT0 -> WAIT0
		transition[State.WAIT1.ordinal()] [Msg.RCV_NOTCORRUPT_SEQ1.ordinal()] = new ProcessPacket();		// WAIT1 -> WAIT0
		transition[State.WAIT1.ordinal()] [Msg.RESEND_ACK0.ordinal()] = new ResendAck();					// WAIT1 -> WAIT1

		System.out.println("INFO FSM constructed, current state: "+currentState);
	}

	public static void main(String[] args) throws FileNotFoundException, SocketException {
		FileReceiver receiver = new FileReceiver(9876);
		receiver.run();

		System.out.println("END - File received ");
	}

	public void run() {
		int count = 0;
		while (!endOfFile) {
			System.out.println("-------------------------------------------------");
			receive();
			if(!corrupt()&&hasSeq(currentState.ordinal())) {
				if(currentState == State.WAIT0) processMsg(Msg.RCV_NOTCORRUPT_SEQ0);
				else processMsg(Msg.RCV_NOTCORRUPT_SEQ1);
				if(++count == numberOfpkts) endOfFile = true;
			}
			else {
				if(currentState == State.WAIT0) processMsg(Msg.RESEND_ACK1);
				else processMsg(Msg.RESEND_ACK0);
			}
		}
	}

	public void receive() {
		try {
			receivingSocket.receive(rcvpkt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean corrupt() {
		long receivedChecksum = ByteBuffer.wrap(rcvpkt.getData(),0,8).getLong();

		CRC32 crc = new CRC32();
		crc.update(rcvpkt.getData(),8,rcvpkt.getLength()-8);
		return receivedChecksum != crc.getValue();
	}

	public boolean hasSeq(int number) {
		int receivedNumber = ByteBuffer.wrap(rcvpkt.getData(),8,4).getInt();
		return number == receivedNumber;
	}

	public void deliverData(byte[]data, int off, int length) {
		try {
			if(firstPkt) {
				numberOfpkts = ByteBuffer.wrap(data).getInt(12);
				System.out.println(numberOfpkts);
				off = off+4;
				length = length -4;
				firstPkt= false;
			}

			file.write(data,off,length);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void sendACK(int SeqNr) {
		try {
			if(SeqNr == 0) receivingSocket.send(new DatagramPacket(ACK0,ACK0.length,rcvpkt.getAddress(),rcvpkt.getPort()));
			if(SeqNr == 1) receivingSocket.send(new DatagramPacket(ACK1,ACK1.length,rcvpkt.getAddress(),rcvpkt.getPort()));
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Process a message (a condition has occurred).
	 * @param input Message or condition that has occurred.
	 */
	public void processMsg(Msg input) {
		System.out.println("INFO Received "+input+" in state "+currentState);
		Transition trans = transition[currentState.ordinal()][input.ordinal()];
		if(trans != null){
			currentState = trans.execute(input);
		}
		System.out.println("INFO State: "+currentState);
	}
	
	/**
	 * Abstract base class for all transitions.
	 * Derived classes need to override execute thereby defining the action
	 * to be performed whenever this transition occurs.
	 */
	abstract class Transition {
		abstract public State execute(Msg input);
	}
	
	class ProcessPacket extends Transition {		
		@Override
		public State execute(Msg input) {

			int headOff = 12;
			deliverData(rcvpkt.getData(),headOff,rcvpkt.getLength()-headOff);
			sendACK(input.ordinal());
			return State.values()[Math.abs(input.ordinal() - 1)];
		}
	}
	
	class ResendAck extends Transition {
		@Override
		public State execute(Msg input) {
			sendACK(input.ordinal()-2);
			return currentState;
		}
	}

}
