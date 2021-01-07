package il.ac.idc.cs.sinkhole;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class SinkholeServer {

    static final int UDP_SIZE = 512;
    static final int DNS_PORT = 53;
    static final int LISTEN_PORT = 5300;
    static final int UDP_HEADER = 12;
    static final int QROPCODEAA = 2;
    static final int RAZRCODE = 3;
    static final int ANCOUNT_HIGH = 6;
    static final int ANCOUNT_LOW = 7;
    static final int NSCOUNT_HIGH = 8;
    static final int NSCOUNT_LOW = 9;
    static final byte NXDOMAIN = 0x3;
    static final byte RA = (byte)0x80;
    static final byte NOTAA = (byte)0xfb;
    static final byte QR = (byte) 0x80;

    public static void main(String[] args) {
        Set<String> blockList = new HashSet<>();
        if (args.length > 0) {
            blockList = launchBlockList(args[0]);
        }

        DatagramPacket r = new DatagramPacket(new byte[UDP_SIZE], UDP_SIZE);
        try (
                DatagramSocket s = new DatagramSocket(LISTEN_PORT);
                DatagramSocket senderSocket = new DatagramSocket()
        ) {
            s.receive(r);
            byte[] data = Arrays.copyOf(r.getData(), r.getLength());
            // save port & address for future response
            int userPort = r.getPort();
            InetAddress userAddress = r.getAddress();
            byte[] response;
            // get domain name
            String domainName = parseDomain(r.getData(), UDP_HEADER);

            // check if the domain is listed as blocked
            if (blockList.contains(domainName)) {
                response = createErrorResponse(data);
            } else {
                // the given domain isn't blocked so we'll iteratively find it
                // Send packet to root
                DatagramPacket query = new DatagramPacket(data, data.length, getRoot(), DNS_PORT);
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
                response = createResponse(data);
            }
            //Forward response to user
            r.setAddress(userAddress);
            r.setPort(userPort);
            r.setData(response);
            s.send(r);
        } catch (Exception e) {
            System.err.println("Error initializing DatagramSockets.");
        }
    }


    /**
     * Given a path to a text file with blocked domains
     *
     * @return Set of those domains
     */
    public static Set<String> launchBlockList(String path) {
        Set<String> setList = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))){
            String line = reader.readLine();
            while (line != null) {
                setList.add(line);
                line = reader.readLine();
            }
        } catch (Exception e){
            System.err.println("Problem reading block-list file");
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
        Random r = new Random();
        char c = (char) (r.nextInt(13) + 97);
        return InetAddress.getByName(c + ".root-servers.net");
    }

    /**
     * Check response's NOERROR field
     *
     * @param data - the payload of received packet
     * @return - true if NOERROR, else false
     */
    public static boolean isNoError(byte[] data) {
        return data[RAZRCODE] == 0;
    }

    /**
     * Check responses ANSWER record
     *
     * @param data - the payload of received packet
     * @return - the ANSWER record from the payload
     */
    public static int getAnswer(byte[] data) {
        int answer = data[ANCOUNT_HIGH];
        return (answer << 8) | (data[ANCOUNT_LOW] & 0xff);
    }

    /**
     * Return the number of AUTHORITY in the payload
     *
     * @param data - the payload from the received packet
     * @return - the number of AUTHORITY in the payload
     */
    public static int getAuthority(byte[] data) {
        int answer = data[NSCOUNT_HIGH];
        return (answer << 8) | (data[NSCOUNT_LOW] & 0xff);
    }

    /**
     * Retrieves the address of next server from packet
     *
     * @param data - payload of DNS response
     * @return - address of next server to send query
     */
    public static InetAddress getNextServer(byte[] data) throws UnknownHostException {
        int i = UDP_HEADER;
        while (data[i] != 0) {
            i += 1;
        }
        // Skip to nameserver in payload
        i += 17;
        String domain = parseDomain(data, i);
        return InetAddress.getByName(domain);
    }

    /**
     * Manipulate the last answer before forwarding to the client.
     * Set the RA bit and unset the AA bit.
     *
     * @param data - the payload of the last DNS request
     * @return - payload to be send back to user
     */
    public static byte[] createResponse(byte[] data) {
        data[RAZRCODE] = (byte) (data[RAZRCODE] | RA);
        data[QROPCODEAA] = (byte) (data[QROPCODEAA] & NOTAA);
        return data;
    }

    /**
     * Parses the byte array starting from index start for domain name.
     * Can also take into account for compressed responses.
     *
     * @param data - the payload of the query or response
     * @param start - the starting index of the domain in the payload
     * @return - the hostname as a string
     */
    public static String parseDomain(byte[] data, int start){
        int i = start;
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
        return nameserver.substring(0, nameserver.length() - 1);
    }

    /**
     * Sets the name error error code, qr and ra bits in the payload and returns it
     *
     * @param data - the query sent by the user
     * @return - the payload with the name error bits set to be sent back to the user
     */
    public static byte[] createErrorResponse(byte[] data){
        data[RAZRCODE] |= NXDOMAIN;
        data[RAZRCODE] = (byte) (data[RAZRCODE] | RA);
        data[QROPCODEAA] = (byte) (data[QROPCODEAA] | QR);
        return data;
    }
}
