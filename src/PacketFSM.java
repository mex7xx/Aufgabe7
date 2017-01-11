import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Created by mx on 20.12.16.
 */
public class PacketFSM {

    private final DatagramPacket packet;

    PacketFSM(byte[] buf, int length, InetAddress inet, int port) {

        packet = new DatagramPacket(buf,length,inet, port);
    }

    public void send() throws IOException {

            DatagramSocket clientSocket = new DatagramSocket();
            clientSocket.send(packet);
    }
}
