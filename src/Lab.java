import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;


public class Lab {
    public static void main(String[] args) throws IOException{
        final int UDP_SIZE = 512;
        final int UDP_PORT = 53;
        final int LISTEN_PORT = 5300;
        /*
        1. Listen on UDP port 5300
        2. Send packet to random root server
        3. while (response code is NOERROR) && (ANSWER in record is 0) && (AUTHORITY > 0) {
            Send the query to the first name server in the AUTHORITY section
           }
        4. Send the final response to the client
         */
        DatagramPacket r = new DatagramPacket(new byte[UDP_SIZE], UDP_SIZE);
        DatagramSocket s = new DatagramSocket(LISTEN_PORT);
        s.receive(r);
        // Send packet to root
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramPacket query = new DatagramPacket(r.getData(), r.getLength(),getRoot(),UDP_PORT);
        InetAddress userAddress = query.getAddress();
        int userPort = query.getPort();
        senderSocket.send(query);
        // Get response from root
        senderSocket.receive(r);
        byte[] data = Arrays.copyOf(r.getData(), r.getLength());
        // Query name servers iteratively
        while (isNoError(data) && getAnswer(data) == 0 && getAuthority(data) > 0){
            InetAddress add = getNextServer(data);
            byte[] newData = createQuery(data);
            senderSocket.send(new DatagramPacket(newData,newData.length, add, UDP_PORT));
            senderSocket.receive(r);
            data = Arrays.copyOf(r.getData(), r.getLength());
        }
        //Forward response to user
        r.setAddress(userAddress);
        r.setPort(userPort);
        senderSocket.send(r);
    }

    /**
     * Randomly decides on a root server
     * @return InetAddress of random root server
     * @throws IOException if hostname of root server not valid (should not happen)
     */
    public static InetAddress getRoot() throws IOException {
        char r =(char)(int)(Math.random() * 13 + 97);
        return InetAddress.getByName(r + ".root-servers.net");
    }

    /**
     * Check response's NOERROR field
     * @param data - the payload of received packet
     * @return - true if NOERROR, else false
     */
    public static boolean isNoError(byte[] data){
        return true;
    }

    /**
     * Check responses ANSWER record
     * @param data - the payload of received packet
     * @return - the ANSWER record from the payload
     */
    public static int getAnswer(byte[] data){
        return 0;
    }

    /**
     * Return the number of AUTHORITY in the payload
     * @param data - the payload from the received packet
     * @return - the number of AUTHORITY in the payload
     */
    public static int getAuthority(byte[] data){
        return data[2] & 0x4;
    }

    /**
     * Retrieves the address of next server from packet
     * @param data - payload of DNS response
     * @return - address of next server to send query
     */
    public static InetAddress getNextServer(byte[] data){

    }

    /**
     * Manipulate the payload from the last packet to forward to next server
     * @param data - the payload last sent
     * @return - the payload to send next server
     */
    public static byte[] createQuery(byte[] data){

    }

}
