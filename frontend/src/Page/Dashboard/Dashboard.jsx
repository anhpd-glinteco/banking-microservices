import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { getLoansByAccount } from '@/services/loanApi';
import './Dashboard.scss';

const Dashboard = ({ account, user }) => {
    const { t } = useTranslation();
    const [activeLoans, setActiveLoans] = useState([]);

    useEffect(() => {
        const loadLoans = async () => {
            try {
                const data = await getLoansByAccount(account.id);
                setActiveLoans(data.filter(loan => loan.status === 'ACTIVE'));
            } catch (error) {
                console.error('Error loading loans:', error);
            }
        };
        loadLoans();
    }, [account.id]);

    // Calculate total loan debt
    const totalLoanDebt = activeLoans.reduce((sum, loan) => 
        sum + parseFloat(loan.remainingAmount), 0
    );

    // Prepare data for pie chart
    const chartData = [
        { name: t('accountBalance'), value: parseFloat(account.balance), color: '#4f46e5' },
        { name: t('loanDebt'), value: totalLoanDebt, color: '#ef4444' }
    ];

    const totalAssets = parseFloat(account.balance);
    const totalLiabilities = totalLoanDebt;
    const netWorth = totalAssets - totalLiabilities;

    // Stats cards data
    const statsCards = [
        {
            icon: '💰',
            title: t('totalAssets'),
            value: totalAssets,
            color: 'green',
            trend: '+5.2%'
        },
        {
            icon: '📊',
            title: t('netWorth'),
            value: netWorth,
            color: 'blue',
            trend: '+3.1%'
        },
        {
            icon: '💳',
            title: t('totalDebt'),
            value: totalLoanDebt,
            color: 'red',
            trend: '-2.4%'
        },
        {
            icon: '🎯',
            title: t('savingsGoal'),
            value: 0,
            color: 'purple',
            trend: '0%'
        }
    ];

    return (
        <div className="dashboard-container">
            {/* Welcome Header */}
            <div className="dashboard-header">
                <div>
                    <h2>{t('hello')}, {user.name}! 👋</h2>
                    <p className="subtitle">{t('dashboardWelcome')}</p>
                </div>
                <div className="header-date">
                    {new Date().toLocaleDateString(t('language') === 'vi' ? 'vi-VN' : 'en-US', {
                        weekday: 'long',
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric'
                    })}
                </div>
            </div>

            {/* Stats Cards */}
            <div className="stats-grid">
                {statsCards.map((card, index) => (
                    <div key={index} className={`stat-card ${card.color}`}>
                        <div className="stat-icon">{card.icon}</div>
                        <div className="stat-content">
                            <h4>{card.title}</h4>
                            <h2>${card.value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</h2>
                            <span className={`trend ${card.trend.startsWith('+') ? 'positive' : card.trend.startsWith('-') ? 'negative' : 'neutral'}`}>
                                {card.trend} {t('thisMonth')}
                            </span>
                        </div>
                    </div>
                ))}
            </div>

            {/* Main Content Grid */}
            <div className="dashboard-grid">
                {/* Account Overview */}
                <div className="dashboard-card account-overview">
                    <div className="card-header">
                        <h3>📈 {t('accountOverview')}</h3>
                    </div>
                    <div className="chart-container">
                        <ResponsiveContainer width="100%" height={250}>
                            <PieChart>
                                <Pie
                                    data={chartData.filter(d => d.value > 0)}
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={60}
                                    outerRadius={90}
                                    paddingAngle={5}
                                    dataKey="value"
                                >
                                    {chartData.map((entry, index) => (
                                        <Cell key={`cell-${index}`} fill={entry.color} />
                                    ))}
                                </Pie>
                                <Tooltip 
                                    formatter={(value) => `$${value.toLocaleString('en-US', { minimumFractionDigits: 2 })}`}
                                />
                            </PieChart>
                        </ResponsiveContainer>
                        <div className="chart-legend">
                            {chartData.filter(d => d.value > 0).map((item, index) => (
                                <div key={index} className="legend-item">
                                    <div className="legend-color" style={{ backgroundColor: item.color }}></div>
                                    <span className="legend-label">{item.name}</span>
                                    <span className="legend-value">
                                        ${item.value.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                {/* Quick Actions */}
                <div className="dashboard-card quick-actions">
                    <div className="card-header">
                        <h3>⚡ {t('quickActions')}</h3>
                    </div>
                    <div className="actions-grid">
                        <button className="action-btn">
                            <span className="action-icon">💸</span>
                            <span className="action-label">{t('transfer')}</span>
                        </button>
                        <button className="action-btn">
                            <span className="action-icon">📱</span>
                            <span className="action-label">{t('qrPay')}</span>
                        </button>
                        <button className="action-btn">
                            <span className="action-icon">💰</span>
                            <span className="action-label">{t('applyForLoan')}</span>
                        </button>
                        <button className="action-btn">
                            <span className="action-icon">📊</span>
                            <span className="action-label">{t('viewHistory')}</span>
                        </button>
                    </div>
                </div>

                {/* Account Summary */}
                <div className="dashboard-card account-summary">
                    <div className="card-header">
                        <h3>🏦 {t('accountSummary')}</h3>
                    </div>
                    <div className="summary-items">
                        <div className="summary-item">
                            <div className="summary-icon">💵</div>
                            <div className="summary-details">
                                <span className="summary-label">{t('accountNumber')}</span>
                                <span className="summary-value">{account.accountNumber}</span>
                            </div>
                            <div className="summary-amount">
                                ${account.balance.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                            </div>
                        </div>

                        {activeLoans.length > 0 && activeLoans.map((loan) => (
                            <div key={loan.id} className="summary-item loan-item">
                                <div className="summary-icon">💳</div>
                                <div className="summary-details">
                                    <span className="summary-label">{t('loanActive')}</span>
                                    <span className="summary-value">{loan.purpose}</span>
                                </div>
                                <div className="summary-amount negative">
                                    -${loan.remainingAmount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                </div>
                            </div>
                        ))}

                        <div className="summary-total">
                            <span>{t('netBalance')}</span>
                            <span className={netWorth >= 0 ? 'positive' : 'negative'}>
                                ${Math.abs(netWorth).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                            </span>
                        </div>
                    </div>
                </div>

                {/* Financial Insights */}
                <div className="dashboard-card insights">
                    <div className="card-header">
                        <h3>💡 {t('financialInsights')}</h3>
                    </div>
                    <div className="insights-list">
                        {totalLoanDebt > 0 && (
                            <div className="insight-item warning">
                                <span className="insight-icon">⚠️</span>
                                <div className="insight-content">
                                    <h4>{t('loanRepaymentDue')}</h4>
                                    <p>{t('youHaveActiveLoans', { count: activeLoans.length })}</p>
                                </div>
                            </div>
                        )}
                        
                        {account.balance > 10000 && (
                            <div className="insight-item success">
                                <span className="insight-icon">✅</span>
                                <div className="insight-content">
                                    <h4>{t('healthyBalance')}</h4>
                                    <p>{t('keepUpGoodWork')}</p>
                                </div>
                            </div>
                        )}

                        {account.balance < 1000 && (
                            <div className="insight-item info">
                                <span className="insight-icon">ℹ️</span>
                                <div className="insight-content">
                                    <h4>{t('lowBalance')}</h4>
                                    <p>{t('considerAddingFunds')}</p>
                                </div>
                            </div>
                        )}

                        {totalLoanDebt === 0 && account.balance > 5000 && (
                            <div className="insight-item success">
                                <span className="insight-icon">🎉</span>
                                <div className="insight-content">
                                    <h4>{t('debtFree')}</h4>
                                    <p>{t('greatFinancialHealth')}</p>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Dashboard;
