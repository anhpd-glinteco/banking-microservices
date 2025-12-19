import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchHistory } from '@/services/api';
import toast from 'react-hot-toast';
import './History.scss';

const History = ({ accountId, accountNumber }) => {
    const { t } = useTranslation();
    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const loadHistory = async () => {
        if (!accountId) return;

        setLoading(true);
        setError('');
        try {
            const data = await fetchHistory(accountId);

            // Parse and map transaction history
            const mapped = data.map(log => {
                let details = {};
                try {
                    details = JSON.parse(log.eventData);
                } catch (e) {
                    details = {};
                }

                // Determine transaction type based on account number
                const isCredit = details.toAccountId === accountNumber;
                const amount = details.amount || 0;

                return {
                    id: log.id,
                    transactionId: log.transactionId,
                    desc: details.description || log.eventType.replace('_', ' '),
                    amount: isCredit ? amount : -amount,
                    type: isCredit ? 'credit' : 'debit',
                    status: details.status || 'COMPLETED',
                    date: new Date(log.timestamp).toLocaleString('vi-VN', {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit'
                    })
                };
            });

            // Sort by most recent first
            mapped.sort((a, b) => b.id - a.id);
            setTransactions(mapped);
        } catch (e) {
            setError(t('couldNotLoadHistory'));
            console.error(e);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadHistory();

        // Auto-refresh every 30 seconds
        const interval = setInterval(loadHistory, 30000);
        return () => clearInterval(interval);
    }, [accountId, accountNumber]);

    return (
        <div className="history-card">
            <div className="history-header">
                <h3>Recent Activity</h3>
                <button
                    className="refresh-btn-small"
                    onClick={loadHistory}
                    disabled={loading}
                    title="Refresh transactions"
                >
                    ↻
                </button>
            </div>

            {error && (
                <div className="error-message">
                    <p>{error}</p>
                    <button onClick={loadHistory}>Retry</button>
                </div>
            )}

            {loading && transactions.length === 0 ? (
                <div className="loading-state">Loading transactions...</div>
            ) : (
                <div className="transaction-list">
                    {transactions.length === 0 ? (
                        <div className="empty-state">
                            <span style={{ fontSize: '2rem' }}>📝</span>
                            <p>No transactions yet</p>
                            <small>Your transaction history will appear here</small>
                        </div>
                    ) : (
                        transactions.map(tx => (
                            <div key={tx.id} className={`transaction-item ${tx.type}`}>
                                <div className="tx-icon">
                                    {tx.type === 'credit' ? '↓' : '↑'}
                                </div>
                                <div className="details">
                                    <h4>{tx.desc}</h4>
                                    <span className="tx-date">{tx.date}</span>
                                    {tx.transactionId && (
                                        <small className="tx-id">ID: {tx.transactionId.substring(0, 8)}...</small>
                                    )}
                                </div>
                                <div className="tx-amount-wrapper">
                                    <div className={`amount ${tx.type}`}>
                                        {tx.type === 'credit' ? '+' : '-'}{Math.abs(tx.amount).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                                    </div>
                                    <span className={`status ${tx.status.toLowerCase()}`}>
                                        {tx.status}
                                    </span>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            )}
        </div>
    );
};

export default History;
