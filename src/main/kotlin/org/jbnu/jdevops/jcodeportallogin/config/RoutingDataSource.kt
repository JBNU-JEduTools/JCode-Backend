import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

enum class DataSourceKey {
    MASTER,
    REPLICA
}

class RoutingDataSource(private val replicaDataSource: DataSource?) : AbstractRoutingDataSource() {

    override fun determineCurrentLookupKey(): Any? {
        // Hikari가 checkout 시 연결 유효성을 관리한다. 라우팅 때마다 SELECT 1을
        // 실행하면 실제 조회 수만큼 검증 쿼리와 로그가 추가되어 병목이 된다.
        return if (TransactionSynchronizationManager.isCurrentTransactionReadOnly() && replicaDataSource != null) {
            DataSourceKey.REPLICA
        } else DataSourceKey.MASTER
    }
}
