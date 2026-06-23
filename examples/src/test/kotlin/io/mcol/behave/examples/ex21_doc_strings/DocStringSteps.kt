/**
 * ## Example 21: Doc Strings
 *
 * A fenced block (`"""` or ` ``` `) after a step is delivered to the generated method as a
 * trailing `docString: String` parameter. Indentation is stripped relative to the opening fence;
 * blank lines and `#` inside the block are preserved.
 */
package io.mcol.behave.examples.ex21_doc_strings

import io.mcol.behave.annotations.BehaveFeature
import kotlin.test.assertEquals

@BehaveFeature("features/21_doc_strings.feature")
class DocStringSteps : DocStringStepsSpec {
    private var sent = ""

    override suspend fun iSendThePayload(docString: String) {
        sent = docString
    }

    override suspend fun theReceivedPayloadIs(docString: String) {
        assertEquals(docString, sent)
        // The fenced content arrived intact, de-indented, with its internal newline preserved.
        assertEquals("line one\nline two", sent)
    }
}
