package com.banjocreek.translatebot.deploy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.ListHostedZonesByNameRequest;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class WebsiteDeployer {

    private static final String BucketName, Domain;

    private static final String SignupObjectName = "signup.html";
    private static final String ThankYouObjectName = "thankyou.html";

    private static final String Tld = "banjocreek.io";

    static {
        Domain = "translate." + Tld;
        BucketName = Domain;
    }

    private final AmazonRoute53Client route53 = new AmazonRoute53Client();

    private final AmazonS3Client s3 = new AmazonS3Client();

    public void deploy() {

        /*
         * check for existence because once created, we aren't going to delete
         * it. Amazon could give the name to someone else. This won't matter
         * when we move CDN.
         */
        final Optional<Bucket> maybeBucket = this.s3.listBuckets()
                .stream()
                .filter(b -> b.getName().equals(BucketName))
                .findAny();
        if (!maybeBucket.isPresent()) {
            this.s3.createBucket(new CreateBucketRequest(BucketName));
        }

        this.s3.setBucketWebsiteConfiguration(BucketName, new BucketWebsiteConfiguration("index.html"));

        /*
         * Zone must exist
         */
        final HostedZone zone = this.route53.listHostedZonesByName(new ListHostedZonesByNameRequest().withDNSName(Tld))
                .getHostedZones()
                .stream()
                .findAny()
                .get();

        final String zoneId = zone.getId().replaceAll("/.*/", "");
        final ResourceRecord record = new ResourceRecord().withValue(Domain + ".s3.amazonaws.com");
        final ResourceRecordSet records = new ResourceRecordSet().withName(Domain + ".")
                .withType(RRType.CNAME)
                .withTTL(60L)
                .withResourceRecords(record);
        final Change change = new Change().withAction(ChangeAction.UPSERT).withResourceRecordSet(records);
        final List<Change> changes = Collections.singletonList(change);

        final ChangeBatch changeBatch = new ChangeBatch().withChanges(changes);
        final ChangeResourceRecordSetsRequest changeRecordsRequest = new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(zoneId).withChangeBatch(changeBatch);
        this.route53.changeResourceRecordSets(changeRecordsRequest);

        upload(SignupObjectName);
        upload(ThankYouObjectName);

    }

    public void update() {

        upload(SignupObjectName);
        upload(ThankYouObjectName);

    }

    private void upload(final String name) {
        final ObjectMetadata objMetadata = new ObjectMetadata();
        objMetadata.setContentType("text/html");
        try (InputStream is = this.getClass().getResourceAsStream(name)) {
            final PutObjectRequest putObjectRequest = new PutObjectRequest(BucketName, name, is, objMetadata);
            this.s3.putObject(putObjectRequest);
        } catch (final IOException e) {
            throw new RuntimeException("cannot upload " + name, e);
        }
        this.s3.setObjectAcl(BucketName, name, CannedAccessControlList.PublicRead);

    }

}
