
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.Function;
import java.util.zip.CRC32;


/**
 * Created by mx on 19.12.16.
 * TODO:
 * 1. File Reader implementieren [x]
 * 2. File zerkleinern - mit read()[x]
 * 3. Checksum implementieren (mit CRC32) [x]
 * 4. Paketstruktur implementieren [x]
 *      Paketstruktur insgesamt 1400 Byte  ->  [Checksum: long 8 Byte | Sequenznummer: int 4 Byte | Daten: 1388 Byte] [x]
 * 5. send() [x]
 * 6. receive() [x]
 *      Implementiere/definiere ACKs (0 u. 1) [x]
 *          Definition: ACK ist ein leeres ByteArray mit Sequenznummer und Checksumme.
 *          [Checksum: long 8 Byte | Sequenznummer: int 4 Byte | Daten(empty): 1388 Byte]
 *
 * 7. Main [x]
 * 8. run -> Run-Methode steuert die Übergänge [x]
 * 9. Verlust implementieren
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
    private int receiverPort;
    // Socket which is used for sending data
    private final DatagramSocket sendingSocket;
    //
    private final String fileName;
    // ByteArray which contains the Data, send to Receiver  // 12 Byte-Header (=4Byte Seqeunznummer + 8Byte Checksum)
    private byte[] dataForReceiver = new byte[1400];
    // ByteArray which contains the Data, send form Receiver
    byte[] dataFromReceiver = new byte[1400];
    // First Packet Boolean
    private boolean firstPkt = true;
    // Packet send to Receiver
    private DatagramPacket sndpkt;
    // Received Packet
    private DatagramPacket rcvpkt;
    // Stream of InputFile
    private FileInputStream inputFile;
    // Offset of SeqNr in the header
    private static final int SeqNrOff = 8;
    // lambda Expression for generating ACKs
    public static final Function<Integer,byte[]> genACK = (seqNr) -> {
        byte[] ACK = new byte[1400];

        byte[] headerSeqNr = ByteBuffer.allocate(4).putInt(seqNr).array();
        System.arraycopy(headerSeqNr,0, ACK, SeqNrOff,headerSeqNr.length);

        CRC32 crc = new CRC32();
        crc.update(ACK, SeqNrOff, ACK.length- SeqNrOff);

        byte[] headerCheckSum = ByteBuffer.allocate(8).putLong(crc.getValue()).array();
        System.arraycopy(headerCheckSum,0,ACK,0,headerCheckSum.length);
        return ACK;
    };
    // ACK0 ByteArray
    private static final byte[] ACK0 = genACK.apply(0);
    // ACK1 ByteArray
    private static final byte[] ACK1 = genACK.apply(1);

    /**
     * Constructor
     *
     * @param inet
     * @param receiverPort
     * @param localFileName
     * @throws SocketException
     */
    public FileSenderFSM(InetAddress inet, int receiverPort, String localFileName) throws SocketException {

        this.inet = inet;                                                       // IP-Adress,
        this.receiverPort =  receiverPort;                                      // Port to which FSM sends the pakets
        this.sendingSocket = new DatagramSocket();
        this.rcvpkt = new DatagramPacket(dataFromReceiver,dataFromReceiver.length);
        this.fileName = localFileName;
        setFileStream(localFileName);


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

    /**
     * Main
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        // INPUT: File To Send
        String localFileName = args[0];        // Program-Argument: READFile.txt

        // CREAT & RUN FSM
        FileSenderFSM FSM = new FileSenderFSM(InetAddress.getLocalHost(),9876,localFileName);   // FSM Initializiation
        FSM.run();

        // FSM Terminated
        System.out.println("File Transfered");
    }

    /**
     * Run Method
     *
     * Directs the transitions of the FSM for sending the InputFile.
     */
    @Override
    public void run() {
        try {
            while(inputFile.available() != 0) {
                System.out.println("-------------------------------------------------");
                processMsg(Msg.RDT_SEND);
                while(!receive() || isCorruptORNotACK()) {
                processMsg(Msg.TIMEOUT);                                                            // Timeout umbenenen in Timeout_OR_Corrupt_NotACK
                }
                processMsg(Msg.RECEIVE_NOTCORRUPT_ISACK);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Sends
     * @param sndpkt
     */
    private void unreliableSend(DatagramPacket sndpkt) {
        sndpkt.setAddress(inet);
        sndpkt.setPort(receiverPort);

        try {
            sendingSocket.send(sndpkt);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Waits for packet to arrive.
     *
     * Returns false if IOExept. is thrown (Here for Example: If Timer runs out). Returns true if implem. Socket receives packet.
     *
     * @return
     */
    private boolean receive() {
        boolean receivedSomething = false;
        try {
            sendingSocket.receive(rcvpkt);
            receivedSomething = true;
        } catch (IOException e) {
        }
        return receivedSomething;
    }

    /**
     * Method checks received packed if it is Corrupt or Not a ACK.
     *
     * @return
     */
    private boolean isCorruptORNotACK() {
        boolean corrupt;

        if(currentState == State.WAIT0ACK){
            corrupt = !Arrays.equals(ACK0,rcvpkt.getData());   // Cheksummenprüfung hier nicht nötig, da getData jedes mal gleich sein muss. (incl. Prüfsumme) - Ein ACK sieht immer gleich aus und hat immer die gleiche Prüfsumme
        } else {
            corrupt = !Arrays.equals(ACK1,rcvpkt.getData());
        }
        return corrupt;
    }

    /**
     * Setter for global FileInputStream
     *
     * @param localFile
     */
    public void setFileStream(String localFile) {
        try {
            inputFile = new FileInputStream(localFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Process a message (a condition has occurred).
     * @param input Message or condition that has occurred.
     */
    public void processMsg(Msg input) {
        System.out.println("INFO Received "+input+" in state "+ currentState);
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

    /**
     * Generiert DatagramPacket which can be send to receiver
     *
     * Joins the data with the header (SeqNr + CheckSum)
     * @param SeqNr
     * @return
     */
    private DatagramPacket makePacket(int SeqNr) {
        byte[] headerSeqNr = ByteBuffer.allocate(4).putInt(SeqNr).array();
        System.arraycopy(headerSeqNr,0, dataForReceiver,8,headerSeqNr.length);              // add headerSeqNr to DataArray

        CRC32 crc = new CRC32();
        crc.update(dataForReceiver,8, dataForReceiver.length-8);
        //System.out.println(crc.getValue());
        byte[] headerCheckSum = ByteBuffer.allocate(8).putLong(crc.getValue()).array();
        System.arraycopy(headerCheckSum,0, dataForReceiver,0,headerCheckSum.length);       // add headerCheckSum to DataArray


        System.out.println(ByteBuffer.wrap(dataForReceiver).getInt(12));
        return new DatagramPacket(dataForReceiver, dataForReceiver.length);
    }

    /**
     * Send Transition
     *
     */
    class RDT_send extends Transition {
        @Override
        public State execute(Msg input) {
            State result = null;
            int timeout = 10000;

            try {
                if(firstPkt){
                    int numberOfpkts = Math.max(1,Math.round((inputFile.available()-1388-4)/1388+1));
                    System.out.println(numberOfpkts);

                    inputFile.read(dataForReceiver,12+4, dataForReceiver.length-12-4);

                    byte[] headerPkts = ByteBuffer.allocate(4).putInt(numberOfpkts).array();

                    System.arraycopy(headerPkts,0,dataForReceiver,12,headerPkts.length);
                    System.out.println(dataForReceiver[15]);


                    /**
                    ByteBuffer.wrap(fileName.getBytes());
                    byte[] headerPkts = ByteBuffer.allocate(4).putInt(fileName.toCharArray().length);
                    System.arraycopy(,0,dataForReceiver,16,);
                     **/

                    firstPkt = false;
                } else {
                    inputFile.read(dataForReceiver,12, dataForReceiver.length-12);      // reads 1388 Bytes and keeps 12Bytes empty for the header
                }


                if(currentState == State.WAIT0) {
                    sndpkt = makePacket(State.WAIT0.ordinal());         // sndpkt = make_pkt(0,checksum,data)
                    unreliableSend(sndpkt);                             // udt_send(sndpkt)
                    sendingSocket.setSoTimeout(timeout);                // start_timer() // Timeout zählt erst runter wenn socket in receive Funktion steht.
                    result = State.WAIT0ACK;
                } else {
                    sndpkt = makePacket(State.WAIT1.ordinal());         // sndpkt = make_pkt(1,checksum,data)
                    unreliableSend(sndpkt);                             // udt_send(sndpkt)
                    sendingSocket.setSoTimeout(timeout);                // start_timer()    //Timeout zählt erst runter wenn socket in receive Funktion steht.
                    result = State.WAIT1ACK;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return result;
        }
    }

    /**
     * Timout Transition
     *
     */
    class Timeout extends Transition {
        @Override
        public State execute(Msg input) {
            unreliableSend(sndpkt);                         // udt_send(sendpkt) // start_timer()

            if(currentState == State.WAIT0ACK) return State.WAIT0ACK;
            else return State.WAIT1ACK;
        }
    }

    /**
     * Receive Transition
     *
     */
    class Receive extends Transition {
        @Override
        public State execute(Msg input) {

            try {
                sendingSocket.setSoTimeout(0);                               // stopTimer
            } catch (SocketException e) {
                e.printStackTrace();
            }
            if (currentState == State.WAIT0ACK) return State.WAIT1;
            else return State.WAIT0;
        }
    }
}



