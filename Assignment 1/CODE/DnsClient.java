/*
Feb. 14th. 2020
Yudi Xie 260712639
LiAng Zhao 260781081
 */
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.SocketException;
import java.net.ConnectException;
import java.net.InetAddress;

public class DnsClient {
    static DatagramSocket userSocket;
    static DatagramPacket userPacket;
    static DatagramPacket severPacket;
    static int timeout = 5000;
    static int maxRetry = 3;
    static int retryNum = 0;
    static int portNum = 53;
    static int type = 0;
    static int pointerOffset = 0;
    static long timeTake = 0;
    static String dnsHost;
    static String name;
    static char[][] nameRecord;
    static int answerOffset = 0;
    static boolean auth;


    public static void main(String args[]){
        if (args.length > 0){
            for (int i=0; i < args.length; i++){
                switch (args[i]){
                    case "-t":
                        timeout = Integer.parseInt(args[i+1]);
                        break;
                    case "-r":
                        maxRetry = Integer.parseInt(args[i+1]);
                        break;
                    case "-p":
                        portNum = Integer.parseInt(args[i+1]);
                        break;
                    case "-mx":
                        type = 1;
                        break;
                    case "-ns":
                        type = 2;
                        break;
                    default:
                        continue;
                }
            }
            if (args[args.length-2] != null){
                dnsHost = args[args.length-2];
            }
            if (args[args.length-1] != null){
                name = args[args.length-1];
            }
        }

        infoPrint();
        sendPacket();

        if(userPacket.getData()[0] == severPacket.getData()[0]){
            System.out.println("Response received after "+(timeTake)/1000.+" seconds ("+retryNum +" ) retries");
        }
        else{
            System.out.println("ERROR\tSocket failed to receive response......");
        }
        printResponse();
    }

    public static void sendPacket(){
        try {
            //Open the socket
            userSocket = new DatagramSocket();
            userSocket.setSoTimeout(timeout);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        byte[] request = makeRequest();
        byte[] response = new byte[1024];
        InetAddress requestAddress = getInetaddress();
        userPacket = new DatagramPacket(request, request.length, requestAddress, portNum);
        severPacket = new DatagramPacket(response, response.length);

        long startTime = System.currentTimeMillis();
        try {
            userSocket.send(userPacket);
            userSocket.receive(severPacket);
        } catch (SocketTimeoutException e){
            System.out.println("ERROR\tTimeout");
            if (retryNum<maxRetry){
                System.out.println("Start retrying......");
                retryNum++;
                sendPacket();
            }
            else{
                System.out.println("ERROR\tRetry limit "+maxRetry+" reached");
            }
        } catch (ConnectException e){
            System.out.println("ERROR\tMake connection failed");
        } catch (SocketException e){
            System.out.println("ERROR\tOpen socket failed");
        } catch (UnknownHostException e){
            System.out.println("ERROR\tHost "+dnsHost+" is wrong");
        }
        catch (IOException e){
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        timeTake = endTime-startTime;
    }

    public static InetAddress getInetaddress(){
        //get the inetaddress
        String[] ipStr = dnsHost.substring(1).split("\\.");
        byte[] ipBuf = new byte[4];
        for(int i = 0; i<4;i++){
            ipBuf[i] = (byte)(Integer.parseInt(ipStr[i])&0xff);
        }
        InetAddress addr = null;
        try {
            addr = InetAddress.getByAddress(name,ipBuf);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return addr;
    }

    public static void infoPrint(){
        System.out.println("DnsClient sending request for "+name);
        System.out.println("Server: "+dnsHost);
        switch (type){
            case 0:
                System.out.println("Request type: A");
                break;
            case 1:
                System.out.println("Request type: MX");
                break;
            case 2:
                System.out.println("Request type: NS");
                break;
        }
    }

    public static byte[] makeRequest(){
        /*
        Write the header
        */
        //write a 8-bits random ID
        ByteBuffer dataBuffer;
        dataBuffer = ByteBuffer.allocate(12);
        byte bytes[] = new byte[2];
        new Random().nextBytes(bytes);
        dataBuffer.put(bytes);
        //write the flags for a standard query: 0000000100000000
        dataBuffer.put(new byte[]{0x01,0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        /*
        Write the address
        */
            int nameRecordSize = 0;
            String[] nameStr = name.split("\\.");
            int[] nameSize = new int[nameStr.length];
            nameRecord = new char[nameStr.length][];
            for (int i = 0; i< nameStr.length; i++){
                nameSize[i] = nameStr[i].length();
            }
            for (int i = 0; i< nameStr.length;i++){
                nameRecord[i] = nameStr[i].toCharArray();
                nameRecordSize += nameRecord[i].length;
            }
            char[] nameByte = new char[nameSize.length+nameRecordSize];
            int nameByteCount = 0;
            for (int i = 0; i< nameStr.length;i++){
                nameByte[nameByteCount] = (char)(nameSize[i]);
                nameByteCount++;
                for (char c: nameRecord[i]){
                    nameByte[nameByteCount] = c;
                    nameByteCount++;
                }
            }
            ByteBuffer addrBuffer = ByteBuffer.allocate(nameByte.length+5); //5 is used to store QTYPE and QCLASS
            for (int i = 0; i < nameByte.length; i++){
                addrBuffer.put((byte)nameByte[i]);
            }
            addrBuffer.put((byte)0x00); //end of address
            addrBuffer.put((byte)0x00);
            switch (type){
                case 0:
                    addrBuffer.put((byte)0x01);
                    break;
                case 1:
                    addrBuffer.put((byte)0x0f);
                    break;
                case 2:
                    addrBuffer.put((byte)0x02);
                    break;
            }
            addrBuffer.put(new byte[]{0x00,0x01}); //always 0x0001 for internet address
            byte[] addrbytes = addrBuffer.array();
            ByteBuffer questionBuffer = ByteBuffer.allocate(12 + addrbytes.length);
            questionBuffer.put(dataBuffer.array());
            questionBuffer.put(addrbytes);
            byte[] questionBytes = questionBuffer.array();
            answerOffset = questionBytes.length;

            return questionBytes;
    }

    /*
    In this method we explain hte response
     */
    public static void printResponse(){
        byte[] response = severPacket.getData();
        System.out.println();

        //get response type and class, then validate
        byte[] responseTypeBytes = new byte[]{response[answerOffset-4],response[answerOffset-3]};
        byte[] responseClassBytes = new byte[]{response[answerOffset-2],response[answerOffset-1]};
        byte[] requestTypeBytes = new byte[]{userPacket.getData()[answerOffset-4],userPacket.getData()[answerOffset-3]};
        byte[] requestClassBytes = new byte[]{userPacket.getData()[answerOffset-2],userPacket.getData()[answerOffset-1]};
        if(responseClassBytes[1] != requestClassBytes[1]){
            System.out.println("ERROR\tthe request Class and response Class does not match!");
        }
        if(responseTypeBytes[1] != requestTypeBytes[1]){
            System.out.println("ERROR\tthe request type and response type does not match!");
        }

        //get Rcode
        switch (getBits(response[3],0,4)){
            case 1:
                System.out.println("ERROR\tFormat error: the name server was unable to interpret the query");
                break;
            case 2:
                System.out.println("ERROR\tServer failure: the name server was unable to process this query due to a problem with the name server");
                break;
            case 3:
                System.out.println("ERROR\tName error: meaningful only for responses from an authoritative name server, this code signifies that " +
                        "the domain name referenced in the query does not exist");
                break;
            case 4:
                System.out.println("ERROR\tNot implemented: the name server does not support the requested kind of query");
                break;
            case 5:
                System.out.println("ERROR\tRefused: the name server refuses to perform the requested operation for policy reasons");
                break;
        }

        //get answer count
        short answerRecordCount = ByteBuffer.wrap(new byte[]{response[6],response[7]}).getShort();
        if (answerRecordCount == 0){
            System.out.println("NOTFOUND");
            return;
        }
        System.out.println("***Answer Section( "+answerRecordCount+" records)");

        //get AA
        int AA = getBits(response[2],2, 1);
        if (AA == 1){
            auth = true;
        }else{
            auth = false;
        }

        //get ns count
        short nsRecordCount = ByteBuffer.wrap(new byte[]{response[8],response[9]}).getShort();

        //print the results
        int tempAnswerOffset = answerOffset;
        for (int i =0; i<answerRecordCount; i++){
            answerOffset += (readRecords(answerOffset, response)-answerOffset);
        }

        //skip the answer section and ns section
        int additionalOffset = skipRecord(response, tempAnswerOffset, nsRecordCount+answerRecordCount);
        //get additional record count
        short additionalRecordCount = ByteBuffer.wrap(new byte[]{response[10], response[11]}).getShort();
        if (additionalRecordCount > 0){
            System.out.println("***Additional Section ("+additionalRecordCount+" records)***");
            for (int i = 0; i<additionalRecordCount; i++){
                additionalOffset += (readRecords(additionalOffset, response) - additionalOffset);
            }
        }


    }

    public static int readRecords(int recordOffset, byte[] response){
        String domain = getDomain(response, recordOffset);
        byte[] typeB = new byte[]{response[recordOffset+pointerOffset],response[recordOffset+pointerOffset+1]};
        //System.out.println(Integer.toHexString(typeB[0])+" "+Integer.toHexString(typeB[1]));
        byte[] classB = new byte[]{response[recordOffset+pointerOffset+2],response[recordOffset+pointerOffset+3]};
        //System.out.println(Integer.toHexString(classB[0])+" "+Integer.toHexString(classB[1]));
        byte[] ttlB = new byte[]{response[recordOffset+pointerOffset+4],response[recordOffset+pointerOffset+5],
                response[recordOffset+pointerOffset+6],response[recordOffset+pointerOffset+7]};
        int ttl = ByteBuffer.wrap(ttlB).getInt();
        byte[] rdLengthB = new byte[]{response[recordOffset+pointerOffset+8], response[recordOffset+pointerOffset+9]};
        short rdLength = ByteBuffer.wrap(rdLengthB).getShort();
        //System.out.println(rdLength);
        String ipaddr = "";
        String domainName = "";
        short preference = 0;
        int totaloffset = 0;
        switch (ByteBuffer.wrap(typeB).getShort()){
            case 1:
                byte[] iphandler = new byte[]{response[recordOffset+pointerOffset+10],response[recordOffset+pointerOffset+11],
                        response[recordOffset+pointerOffset+12],response[recordOffset+pointerOffset+13]};
                for (byte b: iphandler){
                    ipaddr += Byte.toUnsignedInt(b);
                    ipaddr += ".";
                }
                ipaddr = ipaddr.substring(0, ipaddr.length()-1);
                totaloffset = recordOffset+pointerOffset+14;
                System.out.println("IP\t"+ipaddr+"\t"+ttl+"\t"+(auth? "auth":"nonauth"));
                break;
            case 2: case 5:
                int pointer = recordOffset+pointerOffset+10;
                domainName = getDomain(response, pointer);
                totaloffset = pointer+pointerOffset;
                if (ByteBuffer.wrap(typeB).getShort() == 2) {
                    System.out.println("NS\t" + domainName + "\t" + ttl +"\t"+ (auth ? "auth" : "nonauth"));
                }
                else if (ByteBuffer.wrap(typeB).getShort() == 5){
                    System.out.println("CNAME\t" + domainName + "\t"+ ttl +"\t"+ (auth ? "auth" : "nonauth"));
                }
                break;
            case 15:
                int pointer2 = recordOffset+pointerOffset+10;
                preference = ByteBuffer.wrap(new byte[]{response[pointer2], response[pointer2+1]}).getShort();
                pointer2+=2;
                domainName = getDomain(response, pointer2);
                totaloffset = pointer2+pointerOffset;
                System.out.println("MX\t"+domainName+"\t"+preference+"\t"+ttl+"t"+(auth? "auth":"nonauth"));
                break;
        }
        return totaloffset;
    }

    public static int skipRecord(byte[] response, int skipIndex, int skipNum){
        for(int i =0; i<skipNum; i++){
            String skipname = getDomain(response, skipIndex);
            skipIndex += pointerOffset;
            skipIndex += 8;
            int rdlength = ByteBuffer.wrap(new byte[]{response[skipIndex], response[skipIndex+1]}).getShort();
            skipIndex += 2+rdlength;
        }
        return skipIndex;
    }

    public static int getBits(byte b, int start, int length){
        int bit = ((b>>start)&(0xFF>>(8-length)));
        return bit;
    }

    public static String getDomain(byte[] response, int index){
        int wordSize = response[index];
        String domain = "";
        boolean start = true;
        int count = 0;
        while(wordSize != 0){
            if (!start){
                domain += ".";
            }
            if ((wordSize & 0xc0) == (int) 0xc0){
                byte[] offset = { (byte) (response[index] & 0x3F), response[index + 1] };
                ByteBuffer wrapped = ByteBuffer.wrap(offset);
                domain += getDomain(response, wrapped.getShort());
                index += 2;
                count +=2;
                wordSize = 0;
            }
            else{
                String substring = "";
                int subStringSize = response[index];
                for (int i = 0; i< subStringSize;i++){
                    substring += (char) response[index+i+1];
                }
                domain += substring;
                index += wordSize + 1;
                count += wordSize + 1;
                wordSize = response[index];
            }
            start = false;
        }
        pointerOffset = count;
        return domain;
    }
}
