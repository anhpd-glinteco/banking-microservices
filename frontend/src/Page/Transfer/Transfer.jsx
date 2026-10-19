import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchAccountByNumber } from '@/services/api';
import { showNotification } from '@/utils/toast';
import './Transfer.scss';

const Transfer = ({ account, onTransfer }) => {
    const { t } = useTranslation();
    const [to, setTo] = useState('');
    const [amount, setAmount] = useState('');
    const defaultDescription = `${account.accountName || 'Khách hàng'} chuyển khoản`;
    const [description, setDescription] = useState(defaultDescription);
    const [loading, setLoading] = useState(false);
    const [recentRecipients, setRecentRecipients] = useState([]);

    // Load recent recipients from localStorage
    useEffect(() => {
        const saved = localStorage.getItem(`recent_recipients_${account.accountNumber}`);
        if (saved) {
            try {
                setRecentRecipients(JSON.parse(saved));
            } catch (e) {
                console.error('Failed to parse recent recipients:', e);
            }
        }
    }, [account.accountNumber]);

    // Save recipient to recent list
    const saveRecentRecipient = (accountNumber, accountName) => {
        const newRecipient = {
            accountNumber,
            accountName,
            timestamp: Date.now()
        };

        setRecentRecipients(prev => {
            // Remove if exists, then add to front
            const filtered = prev.filter(r => r.accountNumber !== accountNumber);
            const updated = [newRecipient, ...filtered].slice(0, 5); // Keep max 5
            
            // Save to localStorage
            localStorage.setItem(`recent_recipients_${account.accountNumber}`, JSON.stringify(updated));
            return updated;
        });
    };

    // Handle clicking on a recent recipient
    const handleSelectRecipient = async (recipient) => {
        setTo(recipient.accountNumber);
        setRecipientName(recipient.accountName);
    };

    // Format number to Vietnamese format (2.000.000)
    const formatVietnameseCurrency = (value) => {
        if (!value) return '';
        // Remove all non-digit characters
        const numericValue = value.replace(/\D/g, '');
        // Format with dots as thousand separators
        return numericValue.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    };

    // Parse formatted value back to number
    const parseFormattedAmount = (value) => {
        return value.replace(/\./g, '');
    };

    // Handle amount input change
    const handleAmountChange = (e) => {
        const inputValue = e.target.value;
        const numericValue = parseFormattedAmount(inputValue);
        setAmount(numericValue);
    };

    const [recipientName, setRecipientName] = useState('');
    const [checkingAccount, setCheckingAccount] = useState(false);

    // Handle recipient account lookup
    const handleRecipientBlur = async () => {
        if (!to || to.length < 5) {
            setRecipientName('');
            return;
        };

        setCheckingAccount(true);
        const toastId = showNotification.loading(t('loading'), t('loading'));

        try {
            const response = await fetchAccountByNumber(to);
            console.log('Account lookup response:', response);

            // Check if response has data property (axios response structure)
            const accountData = response.data || response;

            if (accountData && accountData.accountName) {
                setRecipientName(accountData.accountName);
                showNotification.success(t('success'), `${t('recipient')}: ${accountData.accountName}`, { id: toastId });
            } else {
                setRecipientName('');
                showNotification.error(t('error'), t('recipientNotFound'), { id: toastId });
            }
        } catch (error) {
            setRecipientName('');
            console.log('Account lookup error', error);
            showNotification.error(t('error'), t('recipientNotFound'), { id: toastId });
        } finally {
            setCheckingAccount(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // Validation
        if (to === account.accountNumber) {
            showNotification.error(t('error'), t('cannotTransferOwn'));
            return;
        }

        const transferAmount = parseFloat(amount);
        if (transferAmount <= 0) {
            showNotification.error(t('error'), t('amountGreaterThanZero'));
            return;
        }

        if (transferAmount > account.balance) {
            showNotification.error(t('error'), `${t('insufficientBalance')}: ${account.balance.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}`);
            return;
        }

        setLoading(true);
        try {
            await onTransfer({ to, amount: transferAmount, description });

            // Save to recent recipients
            if (recipientName) {
                saveRecentRecipient(to, recipientName);
            }

            // Clear form on success
            setTo('');
            setRecipientName('');
            setAmount('');
            setDescription(defaultDescription);

            showNotification.success(t('success'), t('transferCompleted'));
        } catch (error) {
            // Error is already handled by parent, just update state
            console.error('Transfer error:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="transfer-container">
            <div className="transfer-card">
                <div className="card-header">
                    <h3>💸 {t('quickTransfer')}</h3>
                    <p>{t('sendMoneyInstantly')}</p>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="input-group">
                        <label>{t('recipientAccount')} <span className="required">*</span></label>
                        <input
                            value={to}
                            onChange={e => setTo(e.target.value)}
                            onBlur={handleRecipientBlur}
                            placeholder={t('recipientAccount')}
                            required
                            disabled={loading}
                        />
                    </div>
                    {recipientName && (
                        <div className="input-group">
                            <label>{t('recipient')}</label>
                            <input
                                value={recipientName}
                                readOnly
                                disabled
                                style={{ backgroundColor: '#f0fdf4', borderColor: '#10b981', color: '#065f46', fontWeight: 'bold' }}
                            />
                        </div>
                    )}
                    <div className="input-group">
                        <label>{t('amount')} <span className="required">*</span></label>
                        <div className="amount-input-wrapper">
                            <span className="currency">₫</span>
                            <input
                                type="text"
                                value={formatVietnameseCurrency(amount)}
                                onChange={handleAmountChange}
                                placeholder="0"
                                required
                                disabled={loading}
                            />
                        </div>
                        <small className="helper-text">
                            {t('available')}: {account.balance.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                        </small>
                    </div>
                    <div className="input-group">
                        <label>{t('description')} (Optional)</label>
                        <input
                            value={description}
                            onChange={e => setDescription(e.target.value)}
                            placeholder={t('whatsThisFor')}
                            maxLength="100"
                            disabled={loading}
                        />
                    </div>
                    <button type="submit" className="submit-btn" disabled={loading || !to || !amount || !recipientName || checkingAccount}>
                        {loading ? (
                            <>
                                <span className="spinner">⏳</span> {t('processing')}
                            </>
                        ) : (
                            <>
                                <span>💰</span> {t('sendMoney')}
                            </>
                        )}
                    </button>
                </form>
            </div>

            {/* Recent Recipients */}
            {recentRecipients.length > 0 && (
                <div className="recent-recipients-card">
                    <div className="card-header">
                        <h3>👥 {t('recentRecipients')}</h3>
                        <p>{t('quickSelectRecipient')}</p>
                    </div>
                    <div className="recipients-list">
                        {recentRecipients.map((recipient, index) => (
                            <div 
                                key={index} 
                                className={`recipient-item ${to === recipient.accountNumber ? 'active' : ''}`}
                                onClick={() => handleSelectRecipient(recipient)}
                            >
                                <div className="recipient-avatar">
                                    {recipient.accountName.charAt(0).toUpperCase()}
                                </div>
                                <div className="recipient-info">
                                    <div className="recipient-name">{recipient.accountName}</div>
                                    <div className="recipient-account">{recipient.accountNumber}</div>
                                </div>
                                <div className="recipient-arrow">›</div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
};

export default Transfer;
