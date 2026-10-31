import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { generateOtp, registerWithOtp, checkUserExists } from '@/services/api';
import LanguageSwitcher from '@/components/LanguageSwitcher/LanguageSwitcher';
import { showNotification } from '@/utils/toast';
import './Register.scss';
import './Login.scss';

const Register = ({ onRegisterSuccess }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    // Steps: 1 = Details, 2 = Verify OTP
    const [step, setStep] = useState(1);

    // Form Data
    const [formData, setFormData] = useState({
        username: '',
        password: '',
        repassword: '',
        fullName: '',
        email: '',
        phone: '',
        idNumber: '',
        accountName: '',
        otp: ''
    });

    const [loading, setLoading] = useState(false);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSendOtp = async (e) => {
        e.preventDefault();

        // Comprehensive validation before sending OTP
        if (!formData.username || !formData.fullName || !formData.email ||
            !formData.phone || !formData.idNumber || !formData.password ||
            !formData.repassword || !formData.accountName) {
            showNotification.error(t('error'), t('fillAllFields'));
            return;
        }

        if (formData.password !== formData.repassword) {
            showNotification.error(t('error'), t('passwordMismatch'));
            return;
        }

        if (formData.password.length < 6) {
            showNotification.error(t('error'), 'Mật khẩu phải có ít nhất 6 ký tự');
            return;
        }

        if (!/^\S+@\S+\.\S+$/.test(formData.email)) {
            showNotification.error(t('error'), 'Email không hợp lệ');
            return;
        }

        if (!/^[0-9]{9,11}$/.test(formData.phone)) {
            showNotification.error(t('error'), 'Số điện thoại phải có 9-11 chữ số');
            return;
        }

        if (!/^[0-9]{12}$/.test(formData.idNumber)) {
            showNotification.error(t('error'), 'Số CCCD phải có 12 chữ số');
            return;
        }

        setLoading(true);
        const checkId = showNotification.loading(t('processing'), 'Đang kiểm tra thông tin...');

        try {
            // Check if user already exists
            const existsResult = await checkUserExists(formData.email, formData.phone, formData.idNumber);

            // axios interceptor already extracts .data, so existsResult is the actual data
            if (existsResult.emailExists) {
                showNotification.error(t('error'), 'Email này đã được sử dụng. Vui lòng sử dụng email khác!', { id: checkId });
                setLoading(false);
                return;
            }

            if (existsResult.phoneExists) {
                showNotification.error(t('error'), 'Số điện thoại này đã được đăng ký. Vui lòng sử dụng số khác!', { id: checkId });
                setLoading(false);
                return;
            }

            if (existsResult.identificationNumberExists) {
                showNotification.error(t('error'), 'Số CCCD này đã được đăng ký trong hệ thống!', { id: checkId });
                setLoading(false);
                return;
            }

            // All checks passed, send OTP
            showNotification.loading(t('sending'), t('sending'), { id: checkId });
            await generateOtp(formData.email);
            showNotification.success(t('success'), t('otpSent'), { id: checkId });
            setStep(2); // Move to OTP step
        } catch (err) {
            const errorMsg = err.message || 'Có lỗi xảy ra khi kiểm tra thông tin';
            showNotification.error(t('error'), errorMsg, { id: checkId });
        } finally {
            setLoading(false);
        }
    };

    const handleFinalRegister = async (e) => {
        e.preventDefault();
        setLoading(true);
        const tId = showNotification.loading(t('registering'), t('registering'));

        try {
            // 1. Register User with OTP (Backend now automatically creates the account)
            const user = await registerWithOtp({
                username: formData.username,
                password: formData.password,
                fullName: formData.fullName,
                email: formData.email,
                phoneNumber: formData.phone,
                identificationNumber: formData.idNumber,
                otp: formData.otp
            });

            showNotification.success(t('success'), t('registerSuccess'), { id: tId });
            if (onRegisterSuccess) onRegisterSuccess(user);
            navigate('/login');

        } catch (err) {
            showNotification.error(t('error'), t('registerFailed') + ": " + err.message, { id: tId });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">
            <LanguageSwitcher />
            <div className="register-card">
                <h2>{step === 1 ? t('register') : t('otpCode')}</h2>
                <p className="subtitle">{step === 1 ? t('signIn') : `${t('otpSentTo')} ${formData.email}`}</p>

                {step === 1 && (
                    <form onSubmit={handleSendOtp}>
                        <div className="form-grid">
                            <div className="form-group">
                                <label>{t('username')} <span className="required">*</span></label>
                                <input name="username" placeholder={t('username')} value={formData.username} onChange={handleChange} required />
                            </div>
                            <div className="form-group">
                                <label>{t('fullName')} <span className="required">*</span></label>
                                <input name="fullName" placeholder={t('fullName')} value={formData.fullName} onChange={handleChange} required />
                            </div>
                        </div>

                        <div className="form-group">
                            <label>{t('emailAddress')} <span className="required">*</span></label>
                            <input name="email" type="email" placeholder="john@example.com" value={formData.email} onChange={handleChange} required />
                        </div>

                        <div className="form-grid">
                            <div className="form-group">
                                <label>{t('phoneNumber')} <span className="required">*</span></label>
                                <input name="phone" placeholder="0901234567" value={formData.phone} onChange={handleChange} required />
                            </div>
                            <div className="form-group">
                                <label>{t('idNumber')} <span className="required">*</span></label>
                                <input name="idNumber" placeholder="12 digit ID" value={formData.idNumber} onChange={handleChange} required />
                            </div>
                        </div>

                        <div className="form-grid">
                            <div className="form-group">
                                <label>{t('password')} <span className="required">*</span></label>
                                <input name="password" type="password" placeholder="******" value={formData.password} onChange={handleChange} required />
                            </div>
                            <div className="form-group">
                                <label>{t('confirmPassword')} <span className="required">*</span></label>
                                <input name="repassword" type="password" placeholder="******" value={formData.repassword} onChange={handleChange} required />
                            </div>
                        </div>

                        <div className="form-group">
                            <label>{t('accountNickname')} <span className="required">*</span></label>
                            <input name="accountName" placeholder="e.g. My Savings" value={formData.accountName} onChange={handleChange} required />
                        </div>

                        <button type="submit" disabled={loading}>
                            {loading ? `📧 ${t('sending')}` : `📧 ${t('nextVerifyEmail')}`}
                        </button>
                        <Link to="/login" className="link-btn">
                            {t('backToLoginLink')}
                        </Link>
                    </form>
                )}

                {step === 2 && (
                    <form onSubmit={handleFinalRegister}>
                        <div className="form-group">
                            <label>{t('enterOtpCode')} <span className="required">*</span></label>
                            <input
                                name="otp"
                                placeholder="000000"
                                value={formData.otp}
                                onChange={handleChange}
                                className="otp-input"
                                maxLength="6"
                                required
                            />
                        </div>
                        <button type="submit" disabled={loading}>
                            {loading ? `⏳ ${t('registering')}` : `✅ ${t('completeRegistration')}`}
                        </button>
                        <button type="button" className="link-btn" onClick={() => setStep(1)}>
                            ← {t('back')}
                        </button>
                    </form>
                )}
            </div>
        </div>
    );
};

export default Register;
