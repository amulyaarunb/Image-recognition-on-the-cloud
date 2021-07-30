#message is what is gotten from request sqs q
#this will be used in s3 to download the image
#paste the image file path in the computing part(provided by sir)
#take the result and put in s3 bucket

import boto3
s3 = boto3.resource(
        service_name='s3',
        region_name='us-east-1',
        aws_access_key_id='AKIAUNU5GL4LN4KCVBXT',
        aws_secret_access_key='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'
    )

sqs = boto3.resource(
        service_name='sqs',
        region_name='us-east-1',
        aws_access_key_id='AKIAUNU5GL4LN4KCVBXT',
        aws_secret_access_key='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'
    )
 
ec2 = boto3.resource(
        service_name='ec2',
        region_name='us-east-1',
        aws_access_key_id='AKIAUNU5GL4LN4KCVBXT',
        aws_secret_access_key='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'
        )
 
requestq = sqs.get_queue_by_name(QueueName='requestsqscc')
responseq = sqs.get_queue_by_name(QueueName='responsesqscc')
 
#get instance ID
import urllib.request
instanceid = urllib.request.urlopen('http://169.254.169.254/latest/meta-data/instance-id').read().decode()
ids = [instanceid]
while(True):
    
    #getting message request from sqs request queue
    message = requestq.receive_messages(
                AttributeNames=['All'],
                MaxNumberOfMessages=1
                )
    #check if there is no message received and repeat it 2 more times
    i = 0
    while (len(message) == 0 and i < 2):
        message = requestq.receive_messages(
                    AttributeNames=['All'],
                    MaxNumberOfMessages=1
                    )
        i += 1
     
    if (len(message) == 0):
        #terminate the instance if there is no request.
        ec2.instances.filter(InstanceIds=ids).terminate()

    #deleting message from requestq
    message[0].delete()

    # accessing object from s3 and downloading it locally
    # send the path of this locally dowloaded object into the deep learning model
    tmpFileName = ''
    for obj in s3.Bucket('inputbucketcc').objects.all():
        if (obj.key.split('.')[0] == message[0].body):
            keys3=s3.Bucket('inputbucketcc').download_file(obj.key,'/home/ubuntu/classifier/' + obj.key)
            tmpFileName = obj.key

    #deep learning model. store result in a variable
    import torch
    import torchvision.transforms as transforms
    import torchvision.models as models
    from PIL import Image
    import json
    import sys
    import numpy as np

    path = '/home/ubuntu/classifier/' + tmpFileName
    img = Image.open(path)
    model = models.resnet18(pretrained=True)
     
    model.eval()
    img_tensor = transforms.ToTensor()(img).unsqueeze_(0)
    outputs = model(img_tensor)
    _, predicted = torch.max(outputs.data, 1)
     
    with open('/home/ubuntu/classifier/imagenet-labels.json') as f:
        labels = json.load(f)
    result = labels[np.array(predicted)[0]]

    # Create a new message
    output = '(' + message[0].body + ', ' + result + ')'
     
    # Send result to response queue
    responseq.send_message(MessageBody=output)
     
    #sending the result to s3 output bucket
    s3.Bucket('outputbucketcc').put_object(Key=message[0].body, Body=output)
