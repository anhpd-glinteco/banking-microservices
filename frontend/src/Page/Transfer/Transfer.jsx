import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { fetchAccountByNumber } from '@/services/api';
import './Transfer.scss';

const Transfer = ({ account, onTransfer }) => {
    const { t } = useTranslation();
    const [to, setTo] = useState('');
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const [loading, setLoading] = useState(false);

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
        const toastId = toast.loading(t('loading'));
        
        try {
            const response = await fetchAccountByNumber(to);
            console.log('Account lookup response:', response);
            
            // Check if response has data property (axios response structure)
            const accountData = response.data || response;
            
            if (accountData && accountData.accountName) {
                setRecipientName(accountData.accountName);
                toast.success(`${t('recipient')}: ${accountData.accountName}`, { id: toastId });
            } else {
                setRecipientName('');
                toast.error(t('recipientNotFound'), { id: toastId });
            }
        } catch (error) {
            setRecipientName('');
            console.log('Account lookup error', error);
            toast.error(t('recipientNotFound'), { id: toastId });
        } finally {
            setCheckingAccount(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // Validation
        if (to === account.accountNumber) {
            toast.error(t('cannotTransferOwn'));
            return;
        }

        const transferAmount = parseFloat(amount);
        if (transferAmount <= 0) {
            toast.error(t('amountGreaterThanZero'));
            return;
        }

        if (transferAmount > account.balance) {
            toast.error(`${t('insufficientBalance')}: ${account.balance.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}`);
            return;
        }

        setLoading(true);
        try {
            await onTransfer({ to, amount: transferAmount, description });

            // Clear form on success
            setTo('');
            setRecipientName('');
            setAmount('');
            setDescription('');

            toast.success(t('transferCompleted'));
        } catch (error) {
            // Error is already handled by parent, just update state
            console.error('Transfer error:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
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
    );
};

export default Transfer;
