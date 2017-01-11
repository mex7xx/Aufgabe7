import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.Random;

/**
 * Created by mx on 11.01.17.
 *
 *
 * Filter Class
 *
 * Simulates errors
 */
public class FilterSim {
    private DatagramSocket socket;

    private DatagramPacket rcvpkt = new DatagramPacket(new byte[1400],1400);

    public static void main(String... args) {

    }

    public FilterSim(DatagramSocket socket) {
        this.socket = socket;
    }

    /**
     *
     *
     * @return Datagram Packet,
     */
    public DatagramPacket read() throws IOException {

        boolean nothingReceived = true;

        while(nothingReceived) {
            DatagramPacket lastrcvpkt = rcvpkt;
            socket.receive(rcvpkt);					// Auf ankommendes Datagram warten.
            nothingReceived = false;

            double rand = Math.random();

            if (rand <= 0.1) {												// FehlerSimmulation: Paket wird verworfen  == 10%
                nothingReceived = true;                                     // Receiver
            } else {
                if (rand <= 0.5) {                            				// FehlerSimmulation: Bitfehler == 40%
                    byte[] corruptData = rcvpkt.getData();
                    int index = new Random().nextInt(rcvpkt.getLength() - 1);


                    byte[] changedByte = new byte[]{corruptData[index]};
                    changedByte[0]++;
                    System.arraycopy(changedByte, 0, corruptData, index, 1);
                    rcvpkt.setData(corruptData);
                } else {
                    if (rand <= 0.8) {     			 						// FehlerSimulation: Paket wird dupliziert == 30%
                        rcvpkt = lastrcvpkt;
                    }
                }
            }
        }
        return rcvpkt;
    }

}
