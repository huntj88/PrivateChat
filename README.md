# DhiffieChat

End-To-End Encrypted Private Chat

I built this app because I wanted to play with encryption and experiment with building low cost automated cloud
environments.

---

### Architecture, Automation, and Deploying Environments

Due to the cost constraints I placed on myself, I wouldn't be able to use managed relational databases or provisioned
server instances.

The backend is built with AWS lambda, dynamoDB, s3. All of these are pay as you go, which is perfect because I don't really
expect anybody to use this anyway `¯\_(ツ)_/¯`

Creating new environments or modifying existing environments is automated through `Terraform`, and AWS costs are about
$0.06/month during development.

Terraform helps automate creation, configuration, or execution of the following resources and tasks.

* AWS Gateway
* AWS lambda functions
* AWS DynamoDB Tables
* AWS S3 Buckets
* Generating Credentials
* Configuring Access and Permissions
* Generating Config files for the app to connect to new environments

---

### Android Automation

![Input for CI build workflow](readme/androidWorkflowInputs.png)

* The Config files to connect to the server are automatically generated when a new environment is created.


* CI workflow to make builds for any specified branch/environment. Provides easy download access for QA. Prod builds generated
  from the master branch also generate a GitHub release.


* All CI builds are automatically tagged with a generated version name that makes it easy to see how the build was
  generated, and where in the history of repository.

---

### Adding a Contact / Message Exchange Process

* Every user provided piece of data is encrypted before it leaves the device. The encryption process uses a shared key
  derived from a Diffie-Hellman key exchange, so the server is never aware of the credentials required to decrypt the
  data.


* To add a contact you must exchange QR codes which contains the user's
  `alias` as well as a hash of their public key, which is used to identity themselves. The `alias` is also never shared
  with the server.


* With the shared secret generated from Key exchange, both users are able to encrypt and decrypt the AES encrypted
  message shared between them


* A QR code is used because it is a method that can be as secure as you want it to be. A MiTM attack would be the
  primary concern as you don't need to make an account to use the app.


* On the more secure end of the spectrum it would be impractical to MiTM attack two people physically in the same
  location exchanging QR codes. Alternatively you could send a picture of the QR code over any other trusted form of
  communication.

---

### Other Ways your data is kept private and protected

* On Android the Local database is encrypted with SQLCipher. The DB password is randomly generated on first launch and
  is encrypted within Android's `Trusted Execution Environment` where the private key for the DB password is held


* For any authenticated network request the app generates a token containing an expiration time in the near future. This
  token is encrypted using the same method to send secure messages, except this time using the servers public key
  instead of another users public key. The server code uses the token, and the users public key to authenticate the
  network request.


* Any uploaded media is stored as encrypted binary data in s3, where after 14 days the data is automatically deleted.
  The user provided encrypted text is also automatically deleted from DynamoDB after 14 days

---

### Coming soon

* Ephemeral keys: Right now a user has one main private/public Diffie-Hellman key pair which is used for encrypting the
  data. In the future, each message should be encrypted using a different set of credentials, and no key pair should be
  used for more than one message
