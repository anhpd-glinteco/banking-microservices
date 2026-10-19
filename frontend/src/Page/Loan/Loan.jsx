import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { applyForLoan, getLoansByAccount, approveLoan, makeRepayment } from '@/services/loanApi';
import toast from 'react-hot-toast';
import { InputNumber } from 'antd';
import './Loan.scss';

const Loan = ({ account, onRefreshAccount }) => {
    const { t } = useTranslation();
    const [activeView, setActiveView] = useState('apply'); // 'apply' or 'myLoans'
    const [loans, setLoans] = useState([]);
    const [loading, setLoading] = useState(false);

    // Application form state
    const [loanAmount, setLoanAmount] = useState('');
    const [termMonths, setTermMonths] = useState('12');
    const [purpose, setPurpose] = useState('');

    // Repayment form state
    const [selectedLoan, setSelectedLoan] = useState(null);
    const [repaymentAmount, setRepaymentAmount] = useState('');

    useEffect(() => {
        // Load loans on mount to check limit
        loadLoans();
    }, [account.id]);

    useEffect(() => {
        if (activeView === 'myLoans') {
            loadLoans();
        }
    }, [activeView]);

    const loadLoans = async () => {
        setLoading(true);
        try {
            const data = await getLoansByAccount(account.id);
            setLoans(data);
        } catch (error) {
            toast.error(t('failedToLoadLoans'));
            console.error('Error loading loans:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleApplyLoan = async (e) => {
        e.preventDefault();

        // Check maximum 3 loans limit
        const activeLoans = loans.filter(loan => 
            loan.status === 'ACTIVE' || loan.status === 'APPROVED' || loan.status === 'PENDING'
        );
        
        if (activeLoans.length >= 3) {
            toast.error(t('maximumLoansReached'));
            return;
        }

        if (!loanAmount || parseFloat(loanAmount) < 10000000) {
            toast.error(t('minimumLoanAmount'));
            return;
        }

        if (!purpose.trim()) {
            toast.error(t('loanPurposeRequired'));
            return;
        }

        setLoading(true);
        const toastId = toast.loading(t('processingLoanApplication'));

        try {
            const loanData = {
                accountId: account.id,
                amount: parseFloat(loanAmount),
                termMonths: parseInt(termMonths),
                purpose: purpose
            };

            const newLoan = await applyForLoan(loanData);

            // Auto-approve for demo purposes
            await approveLoan(newLoan.id);

            toast.success(t('loanApprovedSuccess'), { id: toastId });

            // Clear form
            setLoanAmount('');
            setPurpose('');

            // Safe check before calling refresh
            if (onRefreshAccount) {
                onRefreshAccount();
            }

            // Switch to loans view
            setActiveView('myLoans');
            loadLoans();
        } catch (error) {
            // Check for specific error types
            if (error.response?.status === 400) {
                toast.error(t('maximumLoansReached'), { id: toastId });
            } else {
                toast.error(t('loanApplicationFailed'), { id: toastId });
            }
        } finally {
            setLoading(false);
        }
    };

    const handleRepayment = async (e) => {
        e.preventDefault();

        if (!repaymentAmount || parseFloat(repaymentAmount) <= 0) {
            toast.error(t('invalidRepaymentAmount'));
            return;
        }

        setLoading(true);
        const toastId = toast.loading(t('processingRepayment'));

        try {
            await makeRepayment(selectedLoan.id, parseFloat(repaymentAmount));
            toast.success(t('repaymentSuccess'), { id: toastId });

            setSelectedLoan(null);
            setRepaymentAmount('');
            loadLoans();
        } catch (error) {
            const errorMsg = error.response?.data?.error || t('repaymentFailed');
            toast.error(errorMsg, { id: toastId });
        } finally {
            setLoading(false);
        }
    };

    const getLoanStatusBadge = (status) => {
        const statusMap = {
            PENDING: { class: 'pending', label: t('loanPending') },
            APPROVED: { class: 'approved', label: t('loanApproved') },
            ACTIVE: { class: 'active', label: t('loanActive') },
            PAID_OFF: { class: 'paid-off', label: t('loanPaidOff') },
            REJECTED: { class: 'rejected', label: t('loanRejected') },
            DEFAULTED: { class: 'defaulted', label: t('loanDefaulted') }
        };

        const statusInfo = statusMap[status] || { class: '', label: status };
        return <span className={`loan-status ${statusInfo.class}`}>{statusInfo.label}</span>;
    };

    return (
        <div className="loan-container">
            <div className="loan-tabs">
                <button
                    className={`tab-btn ${activeView === 'apply' ? 'active' : ''}`}
                    onClick={() => setActiveView('apply')}
                >
                    💰 {t('applyForLoan')}
                </button>
                <button
                    className={`tab-btn ${activeView === 'myLoans' ? 'active' : ''}`}
                    onClick={() => setActiveView('myLoans')}
                >
                    📋 {t('myLoans')}
                </button>
            </div>

            {activeView === 'apply' && (
                <div className="loan-application-card">
                    <div className="card-header">
                        <h3>🏦 {t('loanApplication')}</h3>
                        <p>{t('applyForPersonalLoan')}</p>
                    </div>

                    <form onSubmit={handleApplyLoan}>
                        <div className="input-group">
                            <label>{t('loanAmount')} <span className="required">*</span></label>
                            <input
                                type="text"
                                value={loanAmount ? `₫ ${parseFloat(loanAmount).toLocaleString('vi-VN')}` : ''}
                                onChange={(e) => {
                                    const value = e.target.value.replace(/[₫\s.]/g, '');
                                    setLoanAmount(value ? parseFloat(value) : '');
                                }}
                                placeholder="₫ 10.000.000"
                                disabled={loading}
                            />
                            <small className="helper-text">{t('loanAmountRange')}</small>
                        </div>

                        <div className="input-group">
                            <label>{t('loanTerm')} <span className="required">*</span></label>
                            <select
                                value={termMonths}
                                onChange={(e) => setTermMonths(e.target.value)}
                                disabled={loading}
                                required
                            >
                                <option value="6">6 {t('months')}</option>
                                <option value="12">12 {t('months')}</option>
                                <option value="24">24 {t('months')}</option>
                                <option value="36">36 {t('months')}</option>
                                <option value="60">60 {t('months')}</option>
                                <option value="120">120 {t('months')}</option>
                            </select>
                        </div>

                        <div className="input-group">
                            <label>{t('loanPurpose')} <span className="required">*</span></label>
                            <select
                                value={purpose}
                                onChange={(e) => setPurpose(e.target.value)}
                                disabled={loading}
                                required
                            >
                                <option value="">{t('selectPurpose')}</option>
                                <option value="Personal">{t('personal')}</option>
                                <option value="Business">{t('business')}</option>
                                <option value="Education">{t('education')}</option>
                                <option value="Home Improvement">{t('homeImprovement')}</option>
                                <option value="Medical">{t('medical')}</option>
                                <option value="Debt Consolidation">{t('debtConsolidation')}</option>
                                <option value="Other">{t('other')}</option>
                            </select>
                        </div>

                        <div className="loan-info-box">
                            <p>ℹ️ {t('loanInfoMessage')}</p>
                        </div>

                        <button type="submit" className="submit-btn" disabled={loading}>
                            {loading ? t('processing') : `💳 ${t('submitApplication')}`}
                        </button>
                    </form>
                </div>
            )}

            {activeView === 'myLoans' && (
                <div className="my-loans-section">
                    <div className="loans-header">
                        <h3>📋 {t('yourLoans')}</h3>
                        {loans.length > 0 && (
                            <button className="refresh-btn" onClick={loadLoans} disabled={loading}>
                                {loading ? '🔄' : '↻'}
                            </button>
                        )}
                    </div>

                    {loading && loans.length === 0 ? (
                        <div className="loading-state">{t('loadingLoans')}</div>
                    ) : loans.length === 0 ? (
                        <div className="empty-state">
                            <span style={{ fontSize: '3rem' }}>💰</span>
                            <p>{t('noLoansYet')}</p>
                            <small>{t('applyForFirstLoan')}</small>
                            <button className="btn-primary" onClick={() => setActiveView('apply')}>
                                {t('applyNow')}
                            </button>
                        </div>
                    ) : (
                        <div className="loans-list">
                            {loans.map((loan) => (
                                <div key={loan.id} className="loan-card">
                                    <div className="loan-card-header">
                                        <div>
                                            <h4>{loan.loanAmount.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}</h4>
                                            <span className="loan-purpose">{loan.purpose}</span>
                                        </div>
                                        {getLoanStatusBadge(loan.status)}
                                    </div>

                                    <div className="loan-details">
                                        <div className="detail-item">
                                            <span className="label">{t('remaining')}:</span>
                                            <span className="value">{loan.remainingAmount.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}</span>
                                        </div>
                                        <div className="detail-item">
                                            <span className="label">{t('monthlyPayment')}:</span>
                                            <span className="value">{loan.monthlyPayment.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}</span>
                                        </div>
                                        <div className="detail-item">
                                            <span className="label">{t('interestRate')}:</span>
                                            <span className="value">{loan.interestRate}% {t('annual')}</span>
                                        </div>
                                        <div className="detail-item">
                                            <span className="label">{t('term')}:</span>
                                            <span className="value">{loan.termMonths} {t('months')}</span>
                                        </div>
                                        <div className="detail-item">
                                            <span className="label">{t('appliedOn')}:</span>
                                            <span className="value">
                                                {new Date(loan.createdAt).toLocaleDateString()}
                                            </span>
                                        </div>
                                    </div>

                                    {loan.status === 'ACTIVE' && (
                                        <div className="loan-actions">
                                            {selectedLoan?.id === loan.id ? (
                                                <form onSubmit={handleRepayment} className="repayment-form">
                                                    <div className="input-group-inline">
                                                        <input
                                                            type="text"
                                                            value={repaymentAmount ? parseFloat(repaymentAmount).toLocaleString('vi-VN') : ''}
                                                            onChange={(e) => {
                                                                const value = e.target.value.replace(/\./g, '');
                                                                setRepaymentAmount(value ? parseFloat(value) : '');
                                                            }}
                                                            placeholder="100.000.000"
                                                            required
                                                            disabled={loading}
                                                        />
                                                        <button type="submit" disabled={loading}>
                                                            {t('pay')}
                                                        </button>
                                                        <button
                                                            type="button"
                                                            onClick={() => setSelectedLoan(null)}
                                                            disabled={loading}
                                                        >
                                                            {t('cancel')}
                                                        </button>
                                                    </div>
                                                </form>
                                            ) : (
                                                <button
                                                    className="btn-repay"
                                                    onClick={() => setSelectedLoan(loan)}
                                                >
                                                    💳 {t('makeRepayment')}
                                                </button>
                                            )}
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default Loan;
