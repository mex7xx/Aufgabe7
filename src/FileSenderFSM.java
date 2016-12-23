import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;


/**
 * Created by mx on 19.12.16.
 * TODO:
 * 1. File Reader implementieren
 * 2. File zerkleinern - mit read()
 * 3. Checksum implementieren
 * 4. Paketstruktur implementieren = make_pkt()
 * 5. send()
 * 6. receive()
 * 7. Main
 * 8. Verlust implementieren
 *
 */

public class FileSenderFSM implements Runnable {
    /**
     * Finate State Machine (FSM) Java Example: Woman
     * (lecture Slides for first lecture, p. 19)
     */

    /**
     * Class which models the state machine itself.
     *
     */

    // all states for this FSM
    enum State {
        WAIT0, WAIT1, WAIT0ACK, WAIT1ACK
    };
    // all messages/conditions which can occur
    enum Msg {
        RDT_SEND, TIMEOUT, RECEIVE_NOTCORRUPT_ISACK
        //MEET_MAN, HI, TIME
    }
    // current state of the FSM
    private State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;
    // Inet Adresse
    private InetAddress inet;
    // ServerPort Adresse
    private int serverPort;
    // Socket
    private final DatagramSocket datagramSocket;
    // ACK
    private final DatagramSocket ackSocket;
    // ByteArray which contains the Data, send to Receiver  // 12 Byte-Header (=4Byte Seqeunznummer + 8Byte Checksum)
    private byte[] dataForReceiver = new byte[1400];
    // ByteArray which contains the Data, send form Receiver
    byte[] dataFromReceiver = new byte[1400];
    // Packet send to Receiver
    private DatagramPacket sndpkt;
    // Received Packet
    private DatagramPacket rcvpkt;
    // Stream of InputFile
    private FileInputStream inputFile;
    // ACK0 ByteArray
    private final static byte[] ACK0 = new byte[1400];
    // ACK1 ByteArray
    private final static byte[] ACK1 = new byte[1400];

    /**
     * constructor
     */
    public FileSenderFSM(InetAddress inet, int serverPort, String localFile) throws SocketException {

        this.inet = inet;
        this.serverPort = serverPort;
        this.datagramSocket = new DatagramSocket(serverPort);                                   // mehrere Sockets! In & Out
        this.ackSocket = new DatagramSocket(serverPort+1);
        this.rcvpkt = new DatagramPacket(dataFromReceiver,dataFromReceiver.length);

        byte[] headerSeqNr = ByteBuffer.allocate(4).putInt(0).array();
        System.arraycopy(headerSeqNr,0, ACK0,8,headerSeqNr.length);
        CRC32 crc = new CRC32();
        crc.update(ACK0,8, ACK0.length-8);
        byte[] headerCheckSum = ByteBuffer.allocate(8).putLong(crc.getValue()).array();
        System.arraycopy(headerCheckSum,0, ACK0,0,headerCheckSum.length);       // add headerCheckSum to DataArray

        setFileStream(localFile);

        currentState = State.WAIT0;
        // define all valid state transitions for our state machine
        // (undefined transitions will be ignored)
        transition = new Transition[State.values().length] [Msg.values().length];

        transition[State.WAIT0.ordinal()] [Msg.RDT_SEND.ordinal()]  = new RDT_send();                       // Wait0 -> Wait0ACK
        transition[State.WAIT0ACK.ordinal()] [Msg.TIMEOUT.ordinal()] = new Timeout();                       // Wait0ACK -> Wait0ACK
        transition[State.WAIT0ACK.ordinal()] [Msg.RECEIVE_NOTCORRUPT_ISACK.ordinal()]  = new Receive();     // Wait0ACK -> Wait1
        transition[State.WAIT1.ordinal()] [Msg.RDT_SEND.ordinal()] = new RDT_send();                        // Wait1 -> Wait1ACK
        transition[State.WAIT1ACK.ordinal()] [Msg.TIMEOUT.ordinal()] = new Timeout();                       // Wait1ACK -> Wait1ACK
        transition[State.WAIT1ACK.ordinal()] [Msg.RECEIVE_NOTCORRUPT_ISACK.ordinal()] = new Receive();      // Wait1ACK -> Wait0

        System.out.println("INFO FSM constructed, current state: "+currentState);
    }

    public static void main(String[] args) throws IOException {
        // INPUT: File To Send
        String localFile = args[0];

        // CREAT & RUN FSM
        FileSenderFSM FSM = new FileSenderFSM(InetAddress.getLocalHost(),9876,localFile);
        FSM.run();

        // FSM Terminated
        System.out.println("File Transfered");
    }

    @Override
    public void run() {

        try {
            while(inputFile.available() != 0) {
                processMsg(Msg.RDT_SEND);
                while(!receive() || isCorruptAndNotACK()) {                                            // Was passiert wenn isCorruptAndNotACK?
                    processMsg(Msg.TIMEOUT);                                                            // Timeout umbenenen in Timeout_OR_Corrupt_NotACK
                }
                processMsg(Msg.RECEIVE_NOTCORRUPT_ISACK);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        /**woman.processMsg(Msg.MEET_MAN);
         woman.processMsg(Msg.HI);
         woman.processMsg(Msg.TIME);
         **/
    }

    /**
     *
     * @param SeqNr
     * @return
     */
    private DatagramPacket makePacket(int SeqNr) {
        byte[] headerSeqNr = ByteBuffer.allocate(4).putInt(SeqNr).array();
        System.arraycopy(headerSeqNr,0, dataForReceiver,8,headerSeqNr.length);              // add headerSeqNr to DataArray

        CRC32 crc = new CRC32();
        crc.update(dataForReceiver,8, dataForReceiver.length-8);

        byte[] headerCheckSum = ByteBuffer.allocate(8).putLong(crc.getValue()).array();
        System.arraycopy(headerCheckSum,0, dataForReceiver,0,headerCheckSum.length);       // add headerCheckSum to DataArray

        return new DatagramPacket(dataForReceiver, dataForReceiver.length);
    }
    private void unreliableSend(DatagramPacket sndpkt) {
        sndpkt.setAddress(inet);
        sndpkt.setPort(serverPort);

        try {
            datagramSocket.send(sndpkt);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private boolean receive() {
        boolean receivedSomething = false;
        try {
            ackSocket.receive(rcvpkt);
            receivedSomething = true;
        } catch (IOException e) {
        }
        return receivedSomething;
    }
    private boolean isCorruptAndNotACK() {
        boolean corrupt = true;

        if(currentState == State.WAIT0ACK){
            corrupt = !rcvpkt.getData().equals(ACK0);
        } else {
            corrupt = !rcvpkt.getData().equals(ACK1);
        }
        return corrupt;
    }
    /**
     * Process a message (a condition has occurred).
     * @param input Message or condition that has occurred.
     */
    public void processMsg(Msg input){
        System.out.println("INFO Received "+input+" in state "+currentState);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if(trans != null) {
            currentState = trans.execute(input);
        }
        System.out.println("INFO State: "+ currentState);
    }
    /**
     * Abstract base class for all transitions.
     * Derived classes need to override execute thereby defining the action
     * to be performed whenever this transition occurs.
     */
    abstract class Transition {
        abstract public State execute(Msg input);
    }

    class RDT_send extends Transition {
        @Override
        public State execute(Msg input) {
            State result = null;

            try {

                inputFile.read(dataForReceiver,12, dataForReceiver.length-12);

                if(currentState == State.WAIT0) {

                    sndpkt = makePacket(State.WAIT0.ordinal());
                    unreliableSend(sndpkt);
                    ackSocket.setSoTimeout(1000);                       // Timeout zählt erst runter wenn socket in receive Funktion steht.

                    // sndpkt = make_pkt(0,checksum,data)               // sndpkt ggfs. Global
                    // udt_send(sndpkt)
                    // start_timer()
                    //InetAddress Localhost = InetAddress.getLocalHost();

                    //PacketFSM sndpkt = new PacketFSM(new Byte[100],1, ,2);
                    //sndpkt.send();
                    result = State.WAIT0ACK;
                } else {
                    sndpkt = makePacket(State.WAIT0.ordinal());
                    unreliableSend(sndpkt);
                    ackSocket.setSoTimeout(1000);                       // Timeout zählt erst runter wenn socket in receive Funktion steht.

                    // sndpkt = make_pkt(1,checksum,data)
                    // udt_send(sndpkt)
                    // start_timer()

                    result = State.WAIT1ACK;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }
    }

    class Timeout extends Transition {
        @Override
        public State execute(Msg input) {
            /// udt_send(sendpkt)
            // start_timer()
            unreliableSend(sndpkt);
            if(currentState == State.WAIT0ACK) return State.WAIT0ACK;
            else return State.WAIT1ACK;
        }
    }
    class Receive extends Transition {
        @Override
        public State execute(Msg input) {

            try {
                ackSocket.setSoTimeout(-1);
            } catch (SocketException e) {
                e.printStackTrace();
            }
            // stopTimer

            if (currentState == State.WAIT0ACK) return State.WAIT1;
            else return State.WAIT0;
        }
    }

    public void setFileStream(String localFile) {
        try {
            inputFile = new FileInputStream(localFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}



