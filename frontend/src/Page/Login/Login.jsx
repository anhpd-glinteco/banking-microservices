import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '@/components/LanguageSwitcher/LanguageSwitcher';

import { showNotification } from '@/utils/toast';
import './Login.scss';

const Login = ({ onLogin }) => {
    const { t } = useTranslation();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!username.trim() || !password.trim()) {
            showNotification.error(t('error'), t('enterCredentials'));
            return;
        }

        setLoading(true);
        await onLogin(username, password);
        setLoading(false);
    };

    return (
        <div className="login-page">
            <div className="login-container">
                {/* Left Side: Banner */}
                <div className="login-banner">
                    <div className="banner-content">
                        <h1>ABC Bank</h1>
                        <p>{t('welcome')}</p>
                        <div className="banner-features">
                            <div className="feature">
                                <span className="icon">🔒</span>
                                <span>Secure eKYC</span>
                            </div>
                            <div className="feature">
                                <span className="icon">💳</span>
                                <span>Smart Banking</span>
                            </div>
                            <div className="feature">
                                <span className="icon">📱</span>
                                <span>24/7 Support</span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Right Side: Login Form */}
                <div className="login-form-section">
                    <div className="form-header">
                        <LanguageSwitcher />
                        <div className="logo-section">
                            <span className="bank-logo">🏦</span>
                            <h2>MB Internet Banking</h2> {/* Using MB as requested/implied by "MB" in reference image or just generic */}
                        </div>
                    </div>

                    <div className="login-card-content">
                        <h2>{t('welcome')}</h2>
                        <p className="subtitle">{t('signIn')}</p>

                        <form onSubmit={handleSubmit}>
                            <div className="form-group">
                                <label>{t('username')}</label>
                                <input
                                    type="text"
                                    placeholder={t('username')}
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>
                            <div className="form-group">
                                <label>{t('password')}</label>
                                <input
                                    type="password"
                                    placeholder={t('password')}
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    required
                                />
                            </div>

                            {/* Captcha Placeholder if needed, skipping for now */}

                            <button type="submit" disabled={loading} className="btn-login">
                                {loading ? t('signingIn') : t('login')}
                            </button>

                            <div className="form-links">
                                <Link to="/forgot-password" className="link-btn">
                                    {t('forgotPassword')}
                                </Link>
                                <Link to="/register" className="link-btn">
                                    {t('newUser')}
                                </Link>
                            </div>
                        </form>
                    </div>

                    <div className="form-footer">
                        <p>Hotline: <strong>1900 545 426</strong></p>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Login;
