package test.com.banjocreek.meeting.deploy;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;

public class UpdateLambdaPrototype {

    public static void main(final String[] args) throws Exception {

        final AWSLambdaAsyncClient client = new AWSLambdaAsyncClient();

        final File jarfile = new File(
                "/Users/aztecrex/Code/java-meeting/aws-lambda/target/meeting-lambda-0.0.1-SNAPSHOT.jar");

        final ByteBuffer jarbuf;
        try (FileChannel jarch = FileChannel.open(jarfile.toPath(), StandardOpenOption.READ)) {
            final int jarsz = (int) jarch.size();
            jarbuf = ByteBuffer.allocate(jarsz);
            while (jarbuf.remaining() > 0) {
                jarch.read(jarbuf);
            }
            jarbuf.flip();
        }

        final UpdateFunctionCodeRequest ufrq = new UpdateFunctionCodeRequest().withFunctionName("Info2")
                .withZipFile(jarbuf);

        client.updateFunctionCode(ufrq);

    }

}
