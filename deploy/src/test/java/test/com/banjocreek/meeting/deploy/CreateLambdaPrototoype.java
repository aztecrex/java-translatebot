package test.com.banjocreek.meeting.deploy;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.Runtime;

public class CreateLambdaPrototoype {

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

        final FunctionCode fcod = new FunctionCode().withZipFile(jarbuf);

        final CreateFunctionRequest cfrq = new CreateFunctionRequest().withCode(fcod)
                .withDescription("Prototype function deployent")
                .withFunctionName("Info2")
                .withHandler("com.banjocreek.meeting.lambda.SlackMapInfoHandler::handle")
                .withMemorySize(256)
                .withPublish(true)
                .withRole("arn:aws:iam::299766559344:role/service-role/LambdaExplorerRole")
                .withRuntime(Runtime.Java8);

        client.createFunction(cfrq);

    }

}
