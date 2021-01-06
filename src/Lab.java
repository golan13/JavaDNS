import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class Lab {
    public static void main(String[] args) throws IOException {
        Set<String> blockList = new HashSet<>();
        if (args.length > 0)
            blockList = launchBlockList(args[0]);
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
        // save port & address for future response
        int userPort = r.getPort();
        InetAddress userAddress = r.getAddress();

        // get domain's name
        String domainName = "";
        byte[] data = Arrays.copyOfRange(r.getData(), 13, r.getLength());
        for (byte cell : data) {
            if (cell == 0) break;
            if (cell < 20)
                domainName += '.';
            else
                domainName += (char) cell;
        }

        // check if the domain is listed as blocked
        if (blockList.contains(domainName)) {
            // TODO: return a response with an NXDOMAIN error (AKA “Name Error”, RCODE=3)
        }
        // the given domain isn't blocked so we'll iteratively find it
        else {
            // Send packet to root
            DatagramSocket senderSocket = new DatagramSocket();
            data = Arrays.copyOf(r.getData(), r.getLength());
            DatagramPacket query = new DatagramPacket(data, r.getLength(), getRoot(), DNS_PORT);
            senderSocket.send(query);
            // Get response from root
            senderSocket.receive(r);
            data = Arrays.copyOf(r.getData(), r.getLength());
            // Query name servers iteratively
            while (isNoError(data) && getAnswer(data) == 0 && getAuthority(data) > 0) {
                InetAddress address = getNextServer(data);
                senderSocket.send(new DatagramPacket(query.getData(), query.getLength(), address, DNS_PORT));
                senderSocket.receive(r);
                data = Arrays.copyOf(r.getData(), r.getLength());
            }
        }
        //Forward response to user
        byte[] response = createResponse(data);
        r.setAddress(userAddress);
        r.setPort(userPort);
        r.setData(response);
        s.send(r);
    }


    /**
     * Given a path to a text file with blocked domains
     *
     * @return Set of those domains
     * @throws IOException if the text file couldn't be read
     */
    public static Set<String> launchBlockList(String path) throws IOException {
        Set<String> setList = new HashSet<>();
        BufferedReader reader;
        reader = new BufferedReader(new FileReader(path));
        String line = reader.readLine();
        while (line != null) {
            setList.add(line);
            line = reader.readLine();
        }
        return setList;
    }

    /**
     * Randomly decides on a root server
     *
     * @return InetAddress of random root server
     * @throws UnknownHostException if hostname of root server not valid (should not happen)
     */
    public static InetAddress getRoot() throws UnknownHostException {
        char r = (char) (int) (Math.random() * 13 + 97);
        return InetAddress.getByName(r + ".root-servers.net");
    }

    /**
     * Check response's NOERROR field
     *
     * @param data - the payload of received packet
     * @return - true if NOERROR, else false
     */
    public static boolean isNoError(byte[] data) {
        return data[3] == 0;
    }

    /**
     * Check responses ANSWER record
     *
     * @param data - the payload of received packet
     * @return - the ANSWER record from the payload
     */
    public static int getAnswer(byte[] data) {
        int answer = data[6];
        return (answer << 8) | data[7];
    }

    /**
     * Return the number of AUTHORITY in the payload
     *
     * @param data - the payload from the received packet
     * @return - the number of AUTHORITY in the payload
     */
    public static int getAuthority(byte[] data) {
        int answer = data[8];
        return (answer << 8) | data[9];
    }

    /**
     * Retrieves the address of next server from packet
     *
     * @param data - payload of DNS response
     * @return - address of next server to send query
     */
    public static InetAddress getNextServer(byte[] data) throws UnknownHostException {
        int i = 12;
        while (data[i] != 0) {
            i += 1;
        }
        i += 17;
        StringBuilder nameserver = new StringBuilder();
        while (data[i] != 0) {
            int length = data[i] & 0xff;
            if ((length & 0xc0) == 0xc0) {
                i = data[i + 1];
                length = data[i];
            }
            i += 1;
            for (int j = 0; j < length; j++) {
                nameserver.append((char) (int) data[i + j]);
            }
            i += length;
            nameserver.append(".");
        }
        return InetAddress.getByName(nameserver.substring(0, nameserver.length() - 1));
    }

    /**
     * Manipulate the last answer before forwarding to the client.
     * Set the RA bit and unset the AA bit.
     *
     * @param data - the payload of the last DNS request
     * @return - payload to be send back to user
     */
    public static byte[] createResponse(byte[] data) {
        data[3] = (byte) (data[3] | 0x40);
        data[2] = (byte) (data[2] & 0xfb);
        return data;
    }
}
