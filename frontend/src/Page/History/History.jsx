import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchHistory } from '@/services/api';
import { ArrowUpOutlined, ArrowDownOutlined, CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { Tag, Pagination } from 'antd';
import './History.scss';

const getTransactionTitle = (type, description, eventType, t) => {
    if (description && description.length < 50) return description;

    // Fallback if no description or it's technical
    switch (eventType) {
        case 'TRANSACTION_CREATED':
        case 'MONEY_DEDUCTED':
            return t('transfer'); // "Chuyển tiền"
        case 'COMPENSATION':
            return t('refund'); // "Hoàn tiền"
        case 'CREDIT':
            return t('receiveMoney'); // "Nhận tiền"
        default:
            return t('transaction'); // "Giao dịch"
    }
};

const History = ({ accountNumber, isDashboard = false }) => {
    const { t } = useTranslation();
    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Pagination state
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(isDashboard ? 5 : 10);

    const loadHistory = async () => {
        if (!accountNumber) {
            setTransactions([]);
            setLoading(false);
            return;
        }

        setLoading(true);
        setError('');
        try {
            const data = await fetchHistory(accountNumber);

            const transactionMap = new Map();

            // Parse and merge transaction history
            data.forEach(log => {
                let details = {};
                try {
                    details = JSON.parse(log.eventData);
                } catch {
                    details = {};
                }

                // Determine transaction type based on account number
                const isCredit = log.eventType === 'COMPENSATION' ||
                    log.toAccountNumber === accountNumber || details.toAccountId === accountNumber;
                const amount = details.amount || 0;
                const txId = log.transactionId || log.id; // Fallback to log ID if txId missing

                const mappedItem = {
                    id: log.id, // Keep latest log ID for sorting
                    transactionId: log.transactionId,
                    rawDesc: details.description, // Store raw for quality check
                    eventType: log.eventType,
                    desc: getTransactionTitle(isCredit ? 'credit' : 'debit', details.description, log.eventType, t),
                    amount: amount,
                    type: isCredit ? 'credit' : 'debit',
                    status: details.status || (log.eventType === 'TRANSFER_FAILED' ? 'FAILED' : 'COMPLETED'),
                    timestamp: new Date(log.timestamp)
                };

                if (transactionMap.has(txId)) {
                    const existing = transactionMap.get(txId);

                    // Merge logic: Prefer "SUCCESS" status, prefer specific description over generic
                    // Update if current item has a description and existing doesn't (or is generic)
                    const existingHasDesc = existing.rawDesc && existing.rawDesc.length > 0;
                    const newHasDesc = mappedItem.rawDesc && mappedItem.rawDesc.length > 0;

                    // If new one has description and existing doesn't, take new one
                    if (newHasDesc && !existingHasDesc) {
                        existing.desc = mappedItem.desc;
                        existing.rawDesc = mappedItem.rawDesc; // Update rawDesc too
                    }

                    // Always take the latest status and timestamp if this log is newer
                    if (mappedItem.timestamp > existing.timestamp) {
                        existing.status = mappedItem.status;
                        existing.timestamp = mappedItem.timestamp;
                        existing.id = mappedItem.id; // Update ID to latest for sort
                        // If we didn't update description above, we might check if we should override eventType
                        // But usually description is the most important distinct factor.
                    }

                    // Specific fix: If existing is generic "Giao Dịch" and new one is better, swap.
                    // (Handled by the desc check above potentially, but let's be sure)
                } else {
                    transactionMap.set(txId, mappedItem);
                }
            });

            // Convert map values to array and format date
            const mapped = Array.from(transactionMap.values()).map(item => ({
                ...item,
                date: item.timestamp.toLocaleString('vi-VN', {
                    day: '2-digit',
                    month: '2-digit',
                    year: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                })
            }));

            // Sort by most recent first
            mapped.sort((a, b) => b.timestamp - a.timestamp);
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
    }, [accountNumber]);

    return (
        <div className="history-card">
            <div className="history-header">
                <h3>{t('recentActivity')}</h3>
                <button
                    className="refresh-btn-small"
                    onClick={loadHistory}
                    disabled={loading}
                    title={t('refreshTransactions')}
                >
                    ↻
                </button>
            </div>

            {error && (
                <div className="error-message">
                    <p>{error}</p>
                    <button onClick={loadHistory}>{t('retry')}</button>
                </div>
            )}

            {loading && transactions.length === 0 ? (
                <div className="loading-state">{t('loadingTransactions')}</div>
            ) : (
                <>
                    <div className="transaction-list">
                        {transactions.length === 0 ? (
                            <div className="empty-state">
                                <span style={{ fontSize: '2rem' }}>📝</span>
                                <p>{t('noTransactionsYet')}</p>
                                <small>{t('transactionHistoryWillAppearHere')}</small>
                            </div>
                        ) : (
                            transactions
                                .slice((currentPage - 1) * pageSize, currentPage * pageSize)
                                .map(tx => (
                                    <div key={tx.id} className={`transaction-item ${tx.type}`}>
                                        <div className={`tx-icon-wrapper ${tx.type}`}>
                                            {tx.type === 'credit' ?
                                                <ArrowUpOutlined style={{ fontSize: '18px', color: '#52c41a' }} /> :
                                                <ArrowDownOutlined style={{ fontSize: '18px', color: '#f5222d' }} />
                                            }
                                        </div>
                                        <div className="details">
                                            <h4 className="tx-title">{tx.desc}</h4>
                                            <div className="tx-meta">
                                                <span className="tx-date">{tx.date}</span>
                                                {tx.transactionId && (
                                                    <span className="tx-id">ID: {tx.transactionId.substring(0, 8)}</span>
                                                )}
                                            </div>
                                        </div>
                                        <div className="tx-amount-wrapper">
                                            <div className={`amount ${tx.type}`}>
                                                {tx.type === 'credit' ? '+' : '-'}{parseFloat(tx.amount).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                                            </div>
                                        </div>
                                    </div>
                                ))
                        )}
                    </div>
                    {transactions.length > 0 && (
                        <div className="pagination-container">
                            <Pagination
                                current={currentPage}
                                pageSize={pageSize}
                                total={transactions.length}
                                onChange={(page, size) => {
                                    setCurrentPage(page);
                                    setPageSize(size);
                                }}
                                showSizeChanger
                                pageSizeOptions={['5', '10', '20', '50']}
                                size={isDashboard ? "small" : "default"}
                            />
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default History;
