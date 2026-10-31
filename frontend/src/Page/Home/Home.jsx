
import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { fetchAccount } from '@/services/api';
import websocketService from '@/services/websocket';
import toast from 'react-hot-toast';
import { showNotification } from '@/utils/toast';
import {
    Layout, Menu, Button, Card, Row, Col, Typography,
    Avatar, Dropdown, Space, Progress, List, Tag, Badge, Tabs
} from 'antd';
import {
    HomeOutlined, SwapOutlined, QrcodeOutlined,
    MoneyCollectOutlined, CalculatorOutlined, SettingOutlined, LogoutOutlined,
    UserOutlined, BellOutlined, QuestionCircleOutlined,
    RightOutlined, MenuUnfoldOutlined, MenuFoldOutlined,

    SafetyCertificateOutlined, GiftOutlined, FileTextOutlined,
    ArrowUpOutlined, ArrowDownOutlined
} from '@ant-design/icons';
import Transfer from '@/Page/Transfer/Transfer';
import History from '@/Page/History/History';
import QRCodeGenerator from '@/Page/QRCode/QRCodeGenerator';
import QRCodeScanner from '@/Page/QRCode/QRCodeScanner';
import Loan from '@/Page/Loan/Loan';
import LanguageSwitcher from '@/components/LanguageSwitcher/LanguageSwitcher';
import './Home.scss';

const { Header, Sider, Content } = Layout;
const { Title, Text } = Typography;

const Home = ({ user, account: initialAccount, setAccount: setParentAccount, onLogout, onTransfer }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const location = useLocation();
    const [collapsed, setCollapsed] = useState(false);

    // Get active tab from URL
    const getTabFromPath = () => {
        const path = location.pathname;
        if (path === '/dashboard') return 'dashboard';
        if (path.startsWith('/dashboard/')) {
            return path.replace('/dashboard/', '');
        }
        return 'dashboard';
    };

    const activeTab = getTabFromPath();
    const [account, setAccount] = useState(initialAccount);

    // WebSocket connection for realtime balance updates
    useEffect(() => {
        if (!account?.accountNumber) return;

        const handleBalanceUpdate = (data) => {
            console.log('Balance updated via WebSocket:', data);

            // Handle both object (new format) and simple value (legacy/fallback)
            const newBalance = typeof data === 'object' ? data.newBalance : data;

            setAccount(prev => ({ ...prev, balance: newBalance }));
            if (setParentAccount) {
                setParentAccount(prev => ({ ...prev, balance: newBalance }));
            }

            if (typeof data === 'object' && data.transactionAmount) {
                const isCredit = data.transactionType === 'CREDIT';
                const amount = data.transactionAmount;
                const description = data.description || (isCredit ? `Received from ${data.otherPartyName || 'Unknown'}` : `Sent to ${data.otherPartyName || 'Unknown'}`);

                toast(() => (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{
                            background: isCredit ? '#f6ffed' : '#fff1f0',
                            padding: '8px',
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}>
                            {isCredit ?
                                <ArrowUpOutlined style={{ color: '#52c41a', fontSize: '20px' }} /> :
                                <ArrowDownOutlined style={{ color: '#ff4d4f', fontSize: '20px' }} />
                            }
                        </div>
                        <div>
                            <div style={{ fontWeight: 600, fontSize: '15px' }}>
                                {t('balanceUpdated')}
                            </div>
                            <div style={{
                                color: isCredit ? '#52c41a' : '#ff4d4f',
                                fontWeight: 'bold',
                                fontSize: '14px'
                            }}>
                                {isCredit ? '+' : '-'}{amount.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                            </div>
                            <div style={{ fontSize: '12px', color: '#8c8c8c' }}>
                                {description}
                            </div>
                        </div>
                    </div>
                ), { duration: 4000, id: 'balance-update' });
            } else {
                toast.success(t('balanceUpdated'), { duration: 2000, id: 'balance-update' });
            }
        };

        websocketService.connect(account.accountNumber, handleBalanceUpdate);

        return () => {
            websocketService.disconnect();
        };
    }, [account?.accountNumber, t]); // Only reconnect if account number changes

    // Refresh balance logic (manual)
    const refreshBalance = async () => {
        if (!account?.id) return;
        try {
            const data = await fetchAccount(account.id);
            setAccount(data);
            if (setParentAccount) setParentAccount(data);
            showNotification.success(t('balanceUpdated'));
        } catch {
            showNotification.error(t('failedRefresh'));
        }
    };

    // Removed auto-refresh polling since we now use WebSocket for realtime updates

    const handleTransfer = async (transferData) => {
        await onTransfer(transferData);
        // No need to manually refresh - WebSocket will update balance automatically
    };

    // Menu Item Configuration
    const menuItems = [
        { key: 'dashboard', icon: <HomeOutlined />, label: t('dashboard') },
        { key: 'transfer', icon: <SwapOutlined />, label: t('transfer') },
        { key: 'qr-pay', icon: <QrcodeOutlined />, label: t('qrPay') },
        { key: 'qr-receive', icon: <QrcodeOutlined style={{ transform: 'rotate(180deg)' }} />, label: t('qrReceive') },
        { key: 'loan', icon: <MoneyCollectOutlined />, label: t('applyForLoan') },
        { key: 'history', icon: <FileTextOutlined />, label: t('history') }, // Added explicit history tab
        { key: 'settings', icon: <SettingOutlined />, label: t('settings') },
    ];

    // User Dropdown
    const userMenu = {
        items: [
            {
                key: 'profile',
                label: (
                    <Space>
                        <UserOutlined />
                        {t('accountSettings')}
                    </Space>
                ),
                onClick: () => navigate('/dashboard/settings'),
            },
            {
                type: 'divider',
            },
            {
                key: 'logout',
                label: (
                    <Space style={{ color: '#ff4d4f' }}>
                        <LogoutOutlined />
                        {t('signOut')}
                    </Space>
                ),
                onClick: () => {
                    onLogout();
                    navigate('/login');
                },
            },
        ],
    };

    // Main Dashboard Content
    const DashboardContent = () => (
        <Row gutter={[24, 24]}>
            <Col xs={24} lg={16}>
                <Card title={
                    <Space>
                        <SafetyCertificateOutlined style={{ color: '#1890ff' }} />
                        <span style={{ fontWeight: 600 }}>{t('myAccounts')}</span>
                    </Space>
                } className="main-card">
                    <Row gutter={[24, 24]} align="middle">
                        <Col xs={24} md={10} style={{ textAlign: 'center' }}>
                            <div className="asset-chart-container">
                                <Progress
                                    type="circle"
                                    percent={100}
                                    size={180}
                                    strokeColor="#111d5e"
                                    format={() => (
                                        <div style={{ color: '#111d5e' }}>
                                            <div style={{ fontSize: '24px', fontWeight: 'bold' }}>100%</div>
                                            <div style={{ fontSize: '12px', color: '#8c8c8c' }}>{t('assets')}</div>
                                        </div>
                                    )}
                                />
                            </div>
                            <div className="total-asset-label">
                                <Text type="secondary">{t('totalBalance')}</Text>
                                <Title level={3} style={{ color: '#111d5e', margin: '5px 0' }}>
                                    {account.balance.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                                </Title>
                            </div>
                        </Col>
                        <Col xs={24} md={14}>
                            <List
                                itemLayout="horizontal"
                                dataSource={[
                                    { title: t('accountNumber'), value: account.accountNumber, icon: '🏦', bg: '#e6f7ff' },
                                    { title: 'Credit Cards', value: '1 Card', icon: '💳', bg: '#fff7e6' },
                                    { title: 'Savings', value: '0 VND', icon: '🐷', bg: '#f6ffed' },
                                    { title: 'Loans', value: '0 VND', icon: '📄', bg: '#fff1f0' }
                                ]}
                                renderItem={item => (
                                    <List.Item className="account-list-item">
                                        <List.Item.Meta
                                            avatar={
                                                <Avatar style={{ backgroundColor: item.bg, color: '#333' }}>
                                                    {item.icon}
                                                </Avatar>
                                            }
                                            title={item.title}
                                            description={item.value}
                                        />
                                        <RightOutlined style={{ color: '#ccc' }} />
                                    </List.Item>
                                )}
                            />
                        </Col>
                    </Row>
                </Card>

                <div style={{ marginTop: '24px' }}>
                    <History accountNumber={account.accountNumber} isDashboard={true} />
                </div>
            </Col>

            <Col xs={24} lg={8}>
                <Card title={t('tools')} className="tools-card" style={{ marginBottom: 24 }}>
                    <Tabs defaultActiveKey="1" items={[
                        {
                            key: '1',
                            label: t('interestRate'),
                            children: (
                                <div style={{ textAlign: 'center', padding: '20px' }}>
                                    <CalculatorOutlined style={{ fontSize: '40px', color: '#faad14', marginBottom: 10 }} />
                                    <p>Interest Calculation Tool</p>
                                    <Button type="primary" block>{t('calculateNow')}</Button>
                                </div>
                            )
                        }
                    ]} />
                </Card>

                <Card title={t('promotions')} className="promo-card">
                    <div className="promo-banner">
                        <GiftOutlined style={{ fontSize: '30px', color: '#ff4d4f' }} />
                        <Title level={5}>Refer a Friend</Title>
                        <Text>Get 50,000 VND for each referral!</Text>
                        <Button type="link" block>Learn More</Button>
                    </div>
                </Card>
            </Col>
        </Row>
    );

    const renderContent = () => {
        switch (activeTab) {
            case 'dashboard': return <DashboardContent />;
            case 'transfer': return <Transfer account={account} onTransfer={handleTransfer} />;
            case 'qr-pay': return <QRCodeScanner account={account} onTransfer={handleTransfer} />;
            case 'qr-receive': return <QRCodeGenerator account={account} />;
            case 'loan': return <Loan account={account} onRefreshAccount={refreshBalance} />;
            case 'history': return <History accountNumber={account.accountNumber} />;
            case 'settings': return (
                <Card title={t('accountSettings')} className="main-card">
                    <List>
                        <List.Item>
                            <List.Item.Meta title={t('language')} />
                            <LanguageSwitcher />
                        </List.Item>
                        <List.Item>
                            <List.Item.Meta title={t('accountName')} description={account.accountName} />
                        </List.Item>
                        <List.Item>
                            <List.Item.Meta title={t('accountNumber')} description={account.accountNumber} />
                        </List.Item>
                        <List.Item>
                            <List.Item.Meta title={t('status')} description={<Tag color="green">{t('active')}</Tag>} />
                        </List.Item>
                    </List>
                </Card>
            );
            default: return <DashboardContent />;
        }
    };

    if (!account) return <div style={{ padding: 50, textAlign: 'center' }}>Loading...</div>;

    return (
        <Layout className="home-layout-antd">
            <Sider trigger={null} collapsible collapsed={collapsed} theme="light" className="custom-sider" width={260}>
                <div className="logo-container">
                    <div className="logo-icon">MB</div>
                    {!collapsed && <span className="logo-text">Internet Banking</span>}
                </div>
                <Menu
                    theme="light"
                    mode="inline"
                    selectedKeys={[activeTab]}
                    onClick={(e) => {
                        const path = e.key === 'dashboard' ? '/dashboard' : `/dashboard/${e.key}`;
                        navigate(path);
                    }}
                    items={menuItems}
                    className="custom-menu"
                />
            </Sider>
            <Layout className="site-layout">
                <Header className="site-layout-header">
                    <div className="header-left">
                        {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
                            className: 'trigger',
                            onClick: () => setCollapsed(!collapsed),
                        })}
                        <div className="user-greeting">
                            <div>
                                <Text type="secondary" style={{ fontSize: '12px' }}>{t('welcomeBack')}</Text>
                                <Title level={5} style={{ margin: 0, lineHeight: '1.2' }}>{user.name.toUpperCase()}</Title>
                            </div>
                            <Tag color="gold" style={{ marginLeft: 8 }}>Member</Tag>
                        </div>
                    </div>
                    <div className="header-right">
                        <Space size="large">
                            {/* <LanguageSwitcher /> handled in dropdown or separate icon if needed, avoiding double rendering */}
                            <Badge count={3} dot>
                                <BellOutlined style={{ fontSize: '20px', cursor: 'pointer' }} />
                            </Badge>
                            <QuestionCircleOutlined style={{ fontSize: '20px', cursor: 'pointer' }} />

                            <Dropdown menu={userMenu} placement="bottomRight" arrow>
                                <div className="user-profile-trigger" style={{ cursor: 'pointer' }}>
                                    <Avatar size="large" style={{ backgroundColor: '#111d5e' }}>
                                        {user.name.charAt(0).toUpperCase()}
                                    </Avatar>
                                </div>
                            </Dropdown>
                        </Space>
                    </div>
                </Header>
                <Content className="site-layout-content">
                    {renderContent()}
                </Content>
            </Layout>
        </Layout>
    );
};

export default Home;

