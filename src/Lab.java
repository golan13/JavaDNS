import java.net.DatagramSocket;
import java.net.SocketException;

public class Lab {
    public static void main(String[] args) {
        /*
        1. Listen on UDP port 5300
        2. Send packet to random root server
        3. while (response code is NOERROR) && (ANSWER in record is 0) && (AUTHORITY > 0) {
            Send the query to the first name server in the AUTHORITY section
           }
        4. Send the final response to the client
         */
        try{
            DatagramSocket s = new DatagramSocket(5300);
        } catch (SocketException e) {
            System.out.println("There was a problem creating the socket.");
            System.out.println("The program will now exit");
            return;
        }


    }
}
