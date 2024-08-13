import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowTest {
    @Test
    fun testFlowSkipsUpdatesWhenCollectIsSlow() {
        runTest {
            val flow = MutableStateFlow(0)
            val channel = Channel<Unit>()
            val outValues = mutableListOf<Int>()

            val job = launch {
                flow.collect {
                    channel.receive()
                    outValues += it
                }
            }

            yield()

            for (i in 1..10) {
                flow.update { i }
                yield()
            }

            while (outValues.lastOrNull() != 10) {
                channel.send(Unit)
            }

            assertEquals(listOf(0, 10), outValues)

            job.cancelAndJoin()
        }
    }
}