package com.onesignal.androidsdk

class AssertHelpers {
    static assertResults(results, Closure closure) {
        // 1.Ensure one or more results exist
        assert results

        // 2. Ensure we don't have any failures
        results.each {
            assert !it.value.contains('FAILED')
        }

        // 3. Run test specific asserts
        results.each {
            closure(it)
        }
    }
}
