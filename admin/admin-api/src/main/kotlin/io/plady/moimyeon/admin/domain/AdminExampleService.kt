package io.plady.moimyeon.admin.domain

import org.springframework.stereotype.Service

@Service
class AdminExampleService {
    fun processExample(exampleData: AdminExampleData): AdminExampleResult {
        return AdminExampleResult(exampleData.value)
    }
}
