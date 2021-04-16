terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.24.1"
    }
  }
}

provider "aws" {
  profile = "default"
  region  = "us-east-1"
}

resource "aws_s3_bucket" "bucket" {
  bucket = "encrypted-file-bucket-z00001"
  acl    = "private"

  tags = {
    Name        = "Encrypted file bucket"
    Environment = "Dev"
  }
}

resource "aws_dynamodb_table" "user-dynamodb-table" {
  name           = "User"
  billing_mode   = "PAY_PER_REQUEST"
  read_capacity  = 1
  write_capacity = 1
  hash_key       = "HashedIdentity"

  attribute {
    name = "HashedIdentity"
    type = "S"
  }
}

resource "aws_iam_role" "function_role" {
  name = "function_role"

  # Terraform's "jsonencode" function converts a
  # Terraform expression result to valid JSON syntax.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "s3.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      },
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Sid    = ""
        Principal = {
          Service = "dynamodb.amazonaws.com"
        }
      },
    ]
  })
}


resource "aws_iam_policy" "function_policy" {
  name        = "s3_bucket_policy"
  path        = "/"
  description = "s3 bucket policy"

  # Terraform's "jsonencode" function converts a
  # Terraform expression result to valid JSON syntax.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "s3:ListBucket",
        ]
        Effect   = "Allow"
        Resource = [
          aws_s3_bucket.bucket.arn
        ]
      },
      {
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject"
        ]
        Effect   = "Allow"
        Resource = [
          "${aws_s3_bucket.bucket.arn}/*"
        ]
      },
      {
        Sid = "ReadWriteTable"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:UpdateItem",
          "dynamodb:PutItem",
          "dynamodb:DeleteItem"
        ]
        Effect   = "Allow"
        Resource = [
          aws_dynamodb_table.user-dynamodb-table.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "function-attach" {
  role       = aws_iam_role.function_role.name
  policy_arn = aws_iam_policy.function_policy.arn
}

resource "aws_lambda_function" "hello_name_func" {
  description = "Hello name"
  function_name = "helloname"
  filename = "../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar"
  source_code_hash = filebase64sha256("../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar")
  handler = "me.jameshunt.privatechat.HelloName::handleRequest"
  role = aws_iam_role.function_role.arn
  runtime = "java8"
  timeout = 30
  memory_size = 256
}

resource "aws_lambda_function" "create_identity" {
  description = "Create Identity"
  function_name = "create_identity"
  filename = "../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar"
  source_code_hash = filebase64sha256("../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar")
  handler = "me.jameshunt.privatechat.CreateIdentity::handleRequest"
  role = aws_iam_role.function_role.arn
  runtime = "java8"
  timeout = 30
  memory_size = 256
}

resource "aws_lambda_function" "get_server_public_key" {
  description = "Get Server Public Key"
  function_name = "get_server_public_key"
  filename = "../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar"
  source_code_hash = filebase64sha256("../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar")
  handler = "me.jameshunt.privatechat.GetServerPublicKey::handleRequest"
  role = aws_iam_role.function_role.arn
  runtime = "java8"
  timeout = 30
  memory_size = 256
}

resource "aws_lambda_function" "scan_qr" {
  description = "Scan QR"
  function_name = "scan_qr"
  filename = "../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar"
  source_code_hash = filebase64sha256("../Functions/build/libs/Functions-1.0-SNAPSHOT-all.jar")
  handler = "me.jameshunt.privatechat.ScanQR::handleRequest"
  role = aws_iam_role.function_role.arn
  runtime = "java8"
  timeout = 30
  memory_size = 256
}

resource "aws_api_gateway_rest_api" "chat_gateway" {
  name        = "chat_gateway"
  description = "Chat Gateway"
}

resource "aws_api_gateway_resource" "server_public_key_resource" {
  rest_api_id = aws_api_gateway_rest_api.chat_gateway.id
  parent_id   = aws_api_gateway_rest_api.chat_gateway.root_resource_id
  path_part   = "ServerPublicKey"
}

resource "aws_api_gateway_method" "server_public_key_method" {
  rest_api_id   = aws_api_gateway_rest_api.chat_gateway.id
  resource_id   = aws_api_gateway_resource.server_public_key_resource.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_server_public_key_integration" {
  rest_api_id = aws_api_gateway_rest_api.chat_gateway.id
  resource_id = aws_api_gateway_method.server_public_key_method.resource_id
  http_method = aws_api_gateway_method.server_public_key_method.http_method

  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.get_server_public_key.invoke_arn
}

resource "aws_api_gateway_deployment" "chat_deployment" {
  depends_on = [
    aws_api_gateway_integration.get_server_public_key_integration
  ]

  rest_api_id = aws_api_gateway_rest_api.chat_gateway.id
  stage_name  = "test"
}

resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.get_server_public_key.function_name
  principal     = "apigateway.amazonaws.com"

  # The "/*/*" portion grants access from any method on any resource
  # within the API Gateway REST API.
  source_arn = "${aws_api_gateway_rest_api.chat_gateway.execution_arn}/*/*"
}

output "base_url" {
  value = aws_api_gateway_deployment.chat_deployment.invoke_url
}