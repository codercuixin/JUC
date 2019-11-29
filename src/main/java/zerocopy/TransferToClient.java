package zerocopy;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

/**
 * * @Author: cuixin
 * * @Date: 2019/10/24 11:17
 */
public class TransferToClient {
    public static void main(String[] args) throws Exception {
        TransferToClient sfc = new TransferToClient();
        sfc.testSendFile();
    }

    public void testSendFile() throws Exception {
        InetSocketAddress sad = new InetSocketAddress("127.0.0.1", 9026);
        SocketChannel sc = SocketChannel.open();
        sc.connect(sad);
        sc.configureBlocking(true);
        String fname = "test.txt";
        long fsize = 183678375L, sendzise = 4094;

        FileChannel fc = new FileInputStream(fname).getChannel();
        long start = System.currentTimeMillis();
        long nSent = 0, curSent = 0;
        curSent = fc.transferTo(0, fsize, sc);
        System.out.println("total bytes transferred -- " + curSent + " and time taken in MS --" + (System.currentTimeMillis() - start));
    }
}
