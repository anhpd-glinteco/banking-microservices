import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from '@/components/LanguageSwitcher/LanguageSwitcher';
import toast from 'react-hot-toast';
import './ForgotPassword.scss';
import './Login.scss';

const ForgotPassword = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [step, setStep] = useState(1); // 1: Email, 2: Verify OTP & Reset
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSendOtp = async (e) => {
        e.preventDefault();
        
        if (!email.trim()) {
            toast.error(t('fillAllFields'));
            return;
        }

        setLoading(true);
        const toastId = toast.loading(t('sending'));
        
        try {
            const response = await fetch(`http://localhost:8080/api/accounts/forgot-password?email=${encodeURIComponent(email)}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || t('otpSendFailed'));
            }

            toast.success(t('otpSent'), { id: toastId });
            setStep(2);
        } catch (error) {
            toast.error(error.message || t('otpSendFailed'), { id: toastId });
        } finally {
            setLoading(false);
        }
    };

    const handleReset = async (e) => {
        e.preventDefault();

        if (!otp.trim()) {
            toast.error(t('fillAllFields'));
            return;
        }

        if (!newPassword.trim()) {
            toast.error(t('fillAllFields'));
            return;
        }

        if (newPassword !== confirmPassword) {
            toast.error(t('passwordMismatch'));
            return;
        }

        if (newPassword.length < 6) {
            toast.error(t('passwordTooShort'));
            return;
        }

        setLoading(true);
        const toastId = toast.loading(t('resetting'));

        try {
            const response = await fetch(
                `http://localhost:8080/api/accounts/reset-password?email=${encodeURIComponent(email)}&otp=${encodeURIComponent(otp)}&newPassword=${encodeURIComponent(newPassword)}`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                }
            );

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || t('passwordResetFailed'));
            }

            toast.success(t('passwordResetSuccess'), { id: toastId });
            setTimeout(() => {
                navigate('/login');
            }, 1500);
        } catch (error) {
            toast.error(error.message || t('passwordResetFailed'), { id: toastId });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">
            <LanguageSwitcher />
            <div className="forgot-password-container">
            <div className="forgot-password-card">
                <div className="card-header">
                    <h2>{step === 1 ? `🔒 ${t('forgotPasswordTitle')}` : `🔐 ${t('resetPasswordTitle')}`}</h2>
                    <p>
                        {step === 1 
                            ? t('enterEmail')
                            : `${t('otpSentTo')} ${email}`
                        }
                    </p>
                </div>

                {step === 1 && (
                    <form onSubmit={handleSendOtp}>
                        <div className="input-group">
                            <label>Email Address</label>
                            <input
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                placeholder="your.email@example.com"
                                required
                                disabled={loading}
                            />
                        </div>

                        <div className="button-group">
                            <Link to="/login" className="btn-secondary">
                                ← Back to Login
                            </Link>
                            <button 
                                type="submit" 
                                className="btn-primary"
                                disabled={loading}
                            >
                                {loading ? 'Sending...' : 'Send OTP'}
                            </button>
                        </div>
                    </form>
                )}

                {step === 2 && (
                    <form onSubmit={handleReset}>
                        <div className="input-group">
                            <label>OTP Code</label>
                            <input
                                type="text"
                                value={otp}
                                onChange={(e) => setOtp(e.target.value)}
                                placeholder="Enter 6-digit OTP"
                                maxLength="6"
                                required
                                disabled={loading}
                            />
                            <small className="helper-text">
                                Check your email for the OTP code
                            </small>
                        </div>

                        <div className="input-group">
                            <label>New Password</label>
                            <input
                                type="password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                placeholder="Enter new password"
                                minLength="6"
                                required
                                disabled={loading}
                            />
                        </div>

                        <div className="input-group">
                            <label>Confirm Password</label>
                            <input
                                type="password"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                placeholder="Confirm new password"
                                minLength="6"
                                required
                                disabled={loading}
                            />
                        </div>

                        <div className="button-group">
                            <button 
                                type="button" 
                                className="btn-secondary" 
                                onClick={() => setStep(1)}
                                disabled={loading}
                            >
                                ← Back
                            </button>
                            <button 
                                type="submit" 
                                className="btn-primary"
                                disabled={loading}
                            >
                                {loading ? 'Resetting...' : 'Reset Password'}
                            </button>
                        </div>

                        <div className="resend-section">
                            <button
                                type="button"
                                className="link-button"
                                onClick={handleSendOtp}
                                disabled={loading}
                            >
                                Didn't receive OTP? Resend
                            </button>
                        </div>
                    </form>
                )}
            </div>
        </div>
        </div>
    );
};

export default ForgotPassword;
