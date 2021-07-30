package com.amazonaws.samples;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.ResourceType;

public class instance {

	public static void main(String[] args) throws IOException, InterruptedException {
		
		//Creating credential variable
		BasicAWSCredentials AWS_CREDENTIALS = new BasicAWSCredentials("AKIAI7Q4CA2YYTAOWQIA", "ZVluUZTln3pBnSkolL8urk1WysBfZ08rmHtXlNFL");

		String QueueURL = "https://sqs.us-east-1.amazonaws.com/304198606614/requestsqscc";
		
		int instanceNum = 0;
		
		while(true) {
			//Creating SQS client
			AmazonSQS sqsClient = AmazonSQSClientBuilder.standard()
					.withCredentials(new AWSStaticCredentialsProvider(AWS_CREDENTIALS))
					.withRegion(Regions.US_EAST_1).build();
			
			int numOfRequests[] = new int[3];
			//Get the SQS queue length 3 times.
			for(int i = 0; i < 3; i++) {
				GetQueueAttributesRequest request = new GetQueueAttributesRequest(QueueURL, Arrays.asList("ApproximateNumberOfMessages"));
				GetQueueAttributesResult response = sqsClient.getQueueAttributes(request);
				numOfRequests[i] = Integer.parseInt(response.getAttributes().get("ApproximateNumberOfMessages"));
				System.out.println(numOfRequests[i]);
			}
			//Find the mode of the 3 values received
			int actualNumberOfRequests = numOfRequests[0];
			
			if(numOfRequests[0] == numOfRequests[1] || numOfRequests[0] == numOfRequests[2]) {
				actualNumberOfRequests = numOfRequests[0];
			}
			else if(numOfRequests[1] == numOfRequests[2]) {
				actualNumberOfRequests = numOfRequests[1];
			}
			
			System.out.println("Requests = " + actualNumberOfRequests);
			
			//Creating ec2Client variable with US East Virginia, Ubuntu system image
			AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
					.withCredentials(new AWSStaticCredentialsProvider(AWS_CREDENTIALS)).withRegion(Regions.US_EAST_1).build();

			int runningInstances = numberOfInstancesWithState("running", ec2Client);
			
			System.out.println("Number of running instances = " + runningInstances);
			
			int numberOfInstances = (20 >= (actualNumberOfRequests + runningInstances)) ? actualNumberOfRequests : (20 - runningInstances);
			int i = numberOfInstances;
			
			while(i > 0)
			{
			
				try {
					//Create tags
					TagSpecification tagspecs = new TagSpecification().withResourceType(ResourceType.Instance);
					tagspecs.setTags(Arrays.asList(new Tag("Name", "instance " + instanceNum)));
					Collection<TagSpecification> tagCollection = Arrays.asList(tagspecs);

					RunInstancesRequest runInstancesRequest = new RunInstancesRequest().withImageId("ami-0786613c9b1295798")
							.withInstanceType("t2.micro").withMinCount(1).withMaxCount(1).withKeyName("test1")
							.withSecurityGroupIds("sg-09cb4aab089a6f92a").withTagSpecifications(tagCollection);
					
					//Creating instance variable from the client
					RunInstancesResult runInstancesResult = ec2Client.runInstances(runInstancesRequest);
					Instance instance = runInstancesResult.getReservation().getInstances().get(0);
		
					String instanceId = instance.getInstanceId();
					
					//Start Instance
					StartInstancesRequest startInstancesRequest = new StartInstancesRequest().withInstanceIds(instanceId);
					ec2Client.startInstances(startInstancesRequest);
					
					i--;
					instanceNum++;
					Thread.sleep(3000);
				}
				
				catch(AmazonEC2Exception e) {
					System.out.println(e);
				}
			}

			System.out.println("Processing..");
			
			//Count number of pending instances
			int pendingInstances = numberOfInstancesWithState("pending", ec2Client);;
			
			while(pendingInstances > 0) {
				pendingInstances = numberOfInstancesWithState("pending", ec2Client);
				Thread.sleep(2000);
			}
			System.out.println("Done Waiting for pending instances");
			
			//Count number of running instances with status check initializing
			int statusCheckInstantiatingInstances = numOfInstancesNotPassedStatusCheck(ec2Client);
			int count = 0;
			while(statusCheckInstantiatingInstances > 0 && count < 20) {
				statusCheckInstantiatingInstances = numOfInstancesNotPassedStatusCheck(ec2Client);
				count++;
				Thread.sleep(2000);
			}
			System.out.println("Done Waiting for running instances with status checks not passed");

			Thread.sleep(5000);
		}
	}
	
	//Function to get number of instances with state = state
	private static int numberOfInstancesWithState(String state, AmazonEC2 ec2Client) {
		
		DescribeInstancesRequest instancesRequest = new DescribeInstancesRequest();
		boolean done = false;
		int instances = 0;
		
		while(!done) {
            DescribeInstancesResult instancesresponse = ec2Client.describeInstances(instancesRequest);

            for(Reservation reservation : instancesresponse.getReservations()) {
                for(Instance instance : reservation.getInstances()) {
	                String instanceState = instance.getState().getName();
	                if(instanceState.equals(state)) {
	                	instances++;
	                }
                }
            }

            instancesRequest.setNextToken(instancesresponse.getNextToken());

            if(instancesresponse.getNextToken() == null) {
                done = true;
            }
        }
		return instances;
	}
	
	//Function to get number of instances running with status checks initializing
	private static int numOfInstancesNotPassedStatusCheck(AmazonEC2 ec2Client) {
		
		DescribeInstanceStatusRequest statuscheckrequest = new DescribeInstanceStatusRequest();
		int instances = 0;
		
		DescribeInstanceStatusResult instancesresponse = ec2Client.describeInstanceStatus(statuscheckrequest);
		
        for(InstanceStatus status : instancesresponse.getInstanceStatuses()) {
        	if(status.getInstanceStatus().getStatus().equals("initializing")) {
        		instances++;
        	}
        }
		return instances;
	}
}