package me.jameshunt.privatechat

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class HelloNameTest {

    private val testContext = object: Context {
        override fun getAwsRequestId(): String {
            TODO("Not yet implemented")
        }

        override fun getLogGroupName(): String {
            TODO("Not yet implemented")
        }

        override fun getLogStreamName(): String {
            TODO("Not yet implemented")
        }

        override fun getFunctionName(): String {
            TODO("Not yet implemented")
        }

        override fun getFunctionVersion(): String {
            TODO("Not yet implemented")
        }

        override fun getInvokedFunctionArn(): String {
            TODO("Not yet implemented")
        }

        override fun getIdentity(): CognitoIdentity {
            TODO("Not yet implemented")
        }

        override fun getClientContext(): ClientContext {
            TODO("Not yet implemented")
        }

        override fun getRemainingTimeInMillis(): Int {
            TODO("Not yet implemented")
        }

        override fun getMemoryLimitInMB(): Int {
            TODO("Not yet implemented")
        }

        override fun getLogger(): LambdaLogger {
            TODO("Not yet implemented")
        }

    }

    @Test
    fun testHelloName() {
        val classUnderTest = HelloName()
        val expected = "Hello James"
        val output = classUnderTest.handleRequest("James", testContext)
        assertEquals(expected, output)
    }
}