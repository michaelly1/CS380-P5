//Michael Ly, CS380

import java.nio.ByteBuffer;
import java.util.*;
import java.io.*;
import java.net.*;

public class UdpClient {

    protected static byte[] source = {127, 0, 0, 1};
    protected static byte[] destaddr = {52, 37, 88, (byte) 154};

    public static void main(String[] args)
    {
        try{
            Socket socket = new Socket("codebank.xyz",38005);
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            byte[] deadbeef = {(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF};

            os.write(IPV4UDP(4,deadbeef));

            System.out.print("Handshake response: 0x");
            for(int i = 0; i < 4; i++) {
                int temp = is.read();
                System.out.printf("%02X", temp);
            }

            System.out.println();

            byte port1 = (byte) is.read(), port2 = (byte) is.read();

            int port = (((port1 << 8) & 0xFF00) + (port2 & 0xFF));

            System.out.println("Port number receieved: " + port);

            double totalRTT = 0;

            for(int i = 0; i < 12; i++)
            {
                //UDP header
                int length = (int)Math.pow(2,i+1);
                System.out.println("\nSending packets with " + length + " packets of data");

                byte[] UDPpacket = new byte[8+length];

                Arrays.fill(UDPpacket, (byte) 0);

                //udppacket[0-1] = source port

                UDPpacket[2] = (byte) (port >> 8);
                UDPpacket[3] = (byte) port;

                UDPpacket[4] = (byte) ((length + 8) >> 8);
                UDPpacket[5] = (byte) (length + 8);

                //UDP pseudo header for UDP checksum

                byte[] UDPpsuedo = new byte[20+length];
                Arrays.fill(UDPpsuedo, (byte) 0);

                //source ip set to 0 from [0-3]

                //dest ip addr
                for(int j = 0; j < destaddr.length; j++)
                {
                    UDPpsuedo[4+j] = destaddr[j];
                }

                //set UDP[8] to zero b/c zeros

                UDPpsuedo[9] = (byte) 17;

                //UDP length
                UDPpsuedo[10] = (byte) ((length + 8) >> 8);
                UDPpsuedo[11] = (byte) (length + 8);

                //source port addr = 0

                //dest port addr
                UDPpsuedo[14] = (byte) (port >> 8);
                UDPpsuedo[15] = (byte) port;

                //length
                UDPpsuedo[16] = (byte) ((8+length) >> 8);
                UDPpsuedo[17] = (byte) (8+length);

                //checksum
                UDPpsuedo[18] = 0;
                UDPpsuedo[19] = 0;

                Random rd = new Random();
                byte[] data = new byte[length];
                rd.nextBytes(data);

                for(int j = 0; j < data.length; j++)
                {
                    UDPpsuedo[20+j] = data[j];
                }

                //end of pseudo header

                //calculate checksum
                short pchksum = checksum(UDPpsuedo);
                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.putShort(pchksum);

                UDPpacket[6] = bb.array()[0];
                UDPpacket[7] = bb.array()[1];

                for(int j = 0; j < length; j++)
                {
                    UDPpacket[8+j] = data[j];
                }

                //beginning of RTT
                long start = System.currentTimeMillis();

                os.write(IPV4UDP(8+length, UDPpacket));
                System.out.print("Response: 0x");

                for(int j = 0; j < 4; j++)
                {
                    int temp = is.read();
                    System.out.printf("%02X", temp);
                }

                System.out.println();

                //end of RTT
                long end = System.currentTimeMillis();

                double RTT = end - start;
                System.out.println("RTT: " + RTT + " ms");

                totalRTT += RTT;
            }

            //Avg RTT
            System.out.printf("\nAverage RTT: %.2f ms", totalRTT/12);
            socket.close();

        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public static byte[] IPV4UDP(int n, byte[] b) {

            byte[] Ipv4Packet = new byte[20+n];
            Arrays.fill(Ipv4Packet, (byte) 0);

                //version and header
                int version = 4;
                int hLen = 5;
                byte fbyte = (byte) ((version << 4) | hLen);
                Ipv4Packet[0] = fbyte;

                //no tos

                //Min. length is 20, start off by adding 2
                int Tlength = 20 + n;
                Ipv4Packet[2] = (byte) (Tlength >> 8);
                Ipv4Packet[3] = (byte) Tlength;

                //no id

                //flag, assume no frag so 010
                Ipv4Packet[6] = (byte) 64;

                //no offset

                //ttl
                Ipv4Packet[8] = (byte) 50;

                //protocol = tcp
                Ipv4Packet[9] = (byte) 17;

                //Needed to reset checksum for next iteration
                Ipv4Packet[10] = (byte) 0;
                Ipv4Packet[11] = (byte) 0;

                //source addr equals 0.0.0.0
                for (int j = 0; j < 4; j++) {
                    Ipv4Packet[12 + j] = 0;
                }

                //dest addr
                for (int j = 0; j < destaddr.length; j++) {
                    Ipv4Packet[16 + j] = destaddr[j];
                }

                //calcualte checksum
                short headerchksum = checksum(Ipv4Packet);
                // System.out.printf("\nChecksum calculated: 0x%02X\n", headerchksum );

                ByteBuffer bcs = ByteBuffer.allocate(2);
                bcs.putShort(headerchksum);

                //   System.out.printf("0x%02X\n", bcs.array()[0]);
                //   System.out.printf("0x%02X\n", bcs.array()[1]);

                //checksum, two bytes
                Ipv4Packet[10] = (byte) (bcs.array()[0]);
                Ipv4Packet[11] = (byte) (bcs.array()[1]);

        for(int i = 0; i < b.length; i++)
        {
            Ipv4Packet[20+i] = b[i];
        }

            return Ipv4Packet;
    }

    //from ex.3
    //checksum alg.
    public static short checksum(byte[] b) {
        long sum = 0;
        long temp1, temp2;

        for (int i = 0; i < b.length / 2; i++) {
            temp1 = (b[(i*2)] << 8) & 0xFF00;
            temp2 = (b[(i*2) + 1]) & 0xFF;
            sum += (long) (temp1 + temp2);
            if ((sum & 0xFFFF0000) > 0) {
                sum &= 0xFFFF;
                sum++;
            }
        }

        //handler for odd length byte array
        if (b.length % 2 == 1)
        {
            sum += ((b[b.length-1] << 8) & 0xFF00);
            if ((sum & 0xFFFF0000) > 0) {
                sum &= 0xFFFF;
                sum++;
            }
        }
            return (short) ~(sum & 0xFFFF);
    }
}


