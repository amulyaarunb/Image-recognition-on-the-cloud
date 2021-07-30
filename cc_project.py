import boto3
from flask import Flask, render_template, request
app = Flask(__name__)
from werkzeug.utils import secure_filename 

 

s3 = boto3.client('s3',
                    aws_access_key_id='AKIAUNU5GL4LN4KCVBXT',
                    aws_secret_access_key= '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',
                    ) 

 

sqs = boto3.client('sqs',
                    region_name='us-east-1',
                    aws_access_key_id='AKIAUNU5GL4LN4KCVBXT',
                    aws_secret_access_key= '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',
                    )
BUCKET_NAME='inputbucketcc' 

 

@app.route('/')
def home():
    return render_template("frontend_UI_CC.html")
    

 

@app.route('/upload',methods=['POST'])
def upload():
    #msg = request.files.getlist('file')
    if request.method == 'POST': #and 'img' in request.files:
        if(len(request.files.getlist('img')) > 0):
            for f in request.files.getlist('img'):
                if(f is None or f.filename == ''):
                    print('filename is empty')
                    continue
                filename = secure_filename(f.filename)
                f.save(filename)
               s3.upload_file(
                        Bucket = BUCKET_NAME,
                        Filename=filename,
                        Key = filename
                        )
                sqs.send_message(
                        QueueUrl = 'https://sqs.us-east-1.amazonaws.com/304198606614/requestsqscc',
                        MessageBody = filename.split('.')[0]
                        )
            msg = "Upload the Images ! "
    return render_template("frontend_UI_CC.html", msg = msg)


@app.route('/download', methods=['GET'])
def download():
    if request.method == 'GET':
        queueUrl = 'https://sqs.us-east-1.amazonaws.com/304198606614/responsesqscc'
        attributes = sqs.get_queue_attributes(
            QueueUrl = queueUrl,
            AttributeNames = ['All']
        )
        numberofreqs = attributes['Attributes']['ApproximateNumberOfMessages']
        result = ''
        while(True):
            message = sqs.receive_message(QueueUrl = 'https://sqs.us-east-1.amazonaws.com/304198606614/responsesqscc',
                    AttributeNames=['All'],
                    MaxNumberOfMessages = 1
                )
            if(len(message) > 0 and 'Messages' in message):
                result += message['Messages'][0]['Body'] + '<br/>'
                sqs.delete_message(QueueUrl = 'https://sqs.us-east-1.amazonaws.com/304198606614/responsesqscc',
                    ReceiptHandle = message['Messages'][0]['ReceiptHandle']
                )
            else: