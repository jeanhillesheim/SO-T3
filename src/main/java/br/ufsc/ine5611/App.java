package br.ufsc.ine5611;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class App {

    private static MappedByteBuffer mappedByteBuffer;

    public static void main(String[] args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(args[0]);
        File file = new File(args[1]);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);

        Path pathToTemp = createTempFile(file);

        final Process process = processBuilder.start();
        SignerClient signerClient = new SignerClient(process.getOutputStream(), process.getInputStream());

        signerClient.sign(pathToTemp.toFile());
        process.waitFor();
        signerClient.end();

        readTempFileHash(file);
    }

    private static Path createTempFile(File file) {
        Path path = null;
        try {
            path = Files.createTempFile("temp", null);

            FileChannel fileChannel = (FileChannel) Files.newByteChannel(
                path,
                EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)
            );
            mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, 4 + file.length() + 32);
            fileChannel.close();

            if (mappedByteBuffer != null) {
                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] arrayBytes = new byte[(int) file.length()];
                fileInputStream.read(arrayBytes);
//                RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
//                byte[] arrayBytes = new byte[(int)randomAccessFile.length()];
//                randomAccessFile.readFully(arrayBytes);

                mappedByteBuffer.putInt(0, (int) file.length());

                for (int i = 0; i < arrayBytes.length; i++) mappedByteBuffer.put(4 + i, arrayBytes[i]);

                for (int i = 0; i < 32; i++) mappedByteBuffer.put(4 + (int) file.length() + i, (byte) 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    private static void readTempFileHash(File file) throws IOException {
        byte[] signature = new byte[32];

        for (int i = 0; i < signature.length; i++) {
            signature[i] = mappedByteBuffer.get(4 + (int)file.length() + i);
        }

        System.out.println(Base64.getEncoder().encodeToString(signature));

        byte[] originalFileSignature = getExpectedSignature(file);
        System.out.println(Arrays.equals(originalFileSignature, signature));
    }

    private static byte[] getExpectedSignature(File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
        try (FileInputStream in = new FileInputStream(file)) {
            while (in.available() > 0)
                md.update((byte) in.read());
        }
        return md.digest();
    }
}
