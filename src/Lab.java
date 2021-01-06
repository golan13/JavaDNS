import java.io.IOException;
import java.net.*;
import java.util.Arrays;


public class Lab {
    public static void main(String[] args) throws IOException{
        final int UDP_SIZE = 512;
        final int DNS_PORT = 53;
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
        InetAddress userAddress = r.getAddress();
        int userPort = r.getPort();
        // Send packet to root
        DatagramSocket senderSocket = new DatagramSocket();
        byte[] data = Arrays.copyOf(r.getData(), r.getLength());
        DatagramPacket query = new DatagramPacket(data, r.getLength(),getRoot(),DNS_PORT);
        senderSocket.send(query);
        // Get response from root
        senderSocket.receive(r);
        data = Arrays.copyOf(r.getData(), r.getLength());
        // Query name servers iteratively
        while (isNoError(data) && getAnswer(data) == 0 && getAuthority(data) > 0){
            InetAddress address = getNextServer(data);
            senderSocket.send(new DatagramPacket(query.getData(),query.getLength(), address, DNS_PORT));
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
     * @throws UnknownHostException if hostname of root server not valid (should not happen)
     */
    public static InetAddress getRoot() throws UnknownHostException {
        char r =(char)(int)(Math.random() * 13 + 97);
        return InetAddress.getByName(r + ".root-servers.net");
    }

    /**
     * Check response's NOERROR field
     * @param data - the payload of received packet
     * @return - true if NOERROR, else false
     */
    public static boolean isNoError(byte[] data){
        return data[3] == 0;
    }

    /**
     * Check responses ANSWER record
     * @param data - the payload of received packet
     * @return - the ANSWER record from the payload
     */
    public static int getAnswer(byte[] data){
        int answer = data[6];
        return (answer << 8) | data[7];
    }

    /**
     * Return the number of AUTHORITY in the payload
     * @param data - the payload from the received packet
     * @return - the number of AUTHORITY in the payload
     */
    public static int getAuthority(byte[] data){
        int answer = data[8];
        return (answer << 8) | data[9];
    }

    /**
     * Retrieves the address of next server from packet
     * @param data - payload of DNS response
     * @return - address of next server to send query
     */
    public static InetAddress getNextServer(byte[] data) throws UnknownHostException {
        int i = 12;
        while (data[i] != 0){
            i+=1;
        }
        i+=17;
        String nameserver = "";
        while (data[i] != 0){
            int length = data[i];
            i += 1;
            for (int j = 0; j < length; j++) {
                nameserver += (char)data[i+j];
                i += 1;
            }
            nameserver += ".";
        }
        return InetAddress.getByName(nameserver.substring(0, nameserver.length()-1));
    }
}
