import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Html5QrcodeScanner, Html5Qrcode } from 'html5-qrcode';
import toast from 'react-hot-toast';
import './QRCode.scss';

const QRCodeScanner = ({ account, onTransfer }) => {
    const { t } = useTranslation();
    const [scanning, setScanning] = useState(false);
    const [scannedData, setScannedData] = useState(null);
    const [amount, setAmount] = useState('');
    const [description, setDescription] = useState('');
    const [loading, setLoading] = useState(false);
    const [uploadMode, setUploadMode] = useState(false);

    useEffect(() => {
        let scanner = null;

        if (scanning) {
            scanner = new Html5QrcodeScanner('qr-reader', {
                fps: 10,
                qrbox: { width: 250, height: 250 },
                rememberLastUsedCamera: true,
            });

            scanner.render(onScanSuccess, onScanError);
        }

        function onScanSuccess(decodedText) {
            try {
                const data = JSON.parse(decodedText);
                if (data.accountNumber) {
                    setScannedData(data);
                    setAmount(data.amount || '');
                    setDescription(data.description || '');
                    scanner.clear();
                    setScanning(false);
                    toast.success(t('qrScannedSuccess'));
                } else {
                    toast.error(t('invalidQRFormat'));
                }
            } catch (e) {
                toast.error(t('invalidQRCode'));
            }
        }

        function onScanError(error) {
            // Ignore scan errors (they happen frequently during scanning)
        }

        return () => {
            if (scanner) {
                scanner.clear().catch(() => { });
            }
        };
    }, [scanning]);

    const handleStartScan = () => {
        setScanning(true);
        setScannedData(null);
    };

    const handleStopScan = () => {
        setScanning(false);
    };

    const handleManualInput = () => {
        setScanning(false);
        setScannedData({ accountNumber: '', accountName: t('manualEntry') });
    };

    const handleFileUpload = async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        try {
            const html5QrCode = new Html5Qrcode('qr-reader-file');
            const result = await html5QrCode.scanFile(file, false);
            
            try {
                const data = JSON.parse(result);
                if (data.accountNumber) {
                    setScannedData(data);
                    setAmount(data.amount || '');
                    setDescription(data.description || '');
                    setUploadMode(false);
                    toast.success(t('qrScannedSuccess'));
                } else {
                    toast.error(t('invalidQRFormat'));
                }
            } catch (parseError) {
                toast.error(t('invalidQRCode'));
            }
        } catch (error) {
            toast.error(t('invalidQRCode'));
            console.error('File scan error:', error);
        }
    };

    const handleTransfer = async (e) => {
        e.preventDefault();

        if (!scannedData || !scannedData.accountNumber) {
            toast.error(t('enterRecipientAccount'));
            return;
        }

        if (scannedData.accountNumber === account.accountNumber) {
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
            await onTransfer({
                to: scannedData.accountNumber,
                amount: transferAmount,
                description
            });

            // Reset form
            setScannedData(null);
            setAmount('');
            setDescription('');
            toast.success(t('transferCompletedSuccess'));
        } catch (error) {
            console.error('Transfer error:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="qr-scanner-card">
            <div className="card-header">
                <h3>📷 {t('scanQRToPay')}</h3>
                <p>{t('scanRecipientQR')}</p>
            </div>
            <div id="qr-reader-file" style={{ display: 'none' }}></div>

            {!scanning && !scannedData && (
                <div className="scan-options">
                    <button className="btn-primary scan-btn" onClick={handleStartScan}>
                        📷 {t('startCamera')}
                    </button>
                    <label className="btn-secondary upload-btn" style={{ cursor: 'pointer', margin: '0 0.5rem' }}>
                        🖼️ {t('uploadQRImage')}
                        <input
                            type="file"
                            accept="image/*"
                            onChange={handleFileUpload}
                            style={{ display: 'none' }}
                        />
                    </label>
                </div>
            )}

            {scanning && (
                <div className="scanner-container">
                    <div id="qr-reader"></div>
                    <button className="btn-secondary" onClick={handleStopScan}>
                        ✕ {t('cancelScan')}
                    </button>
                </div>
            )}

            {scannedData && !scanning && (
                <form onSubmit={handleTransfer} className="transfer-form">
                    <div className="scanned-info">
                        <div className="info-row">
                            <span>{t('recipient')}:</span>
                            <strong>{scannedData.accountName || 'Unknown'}</strong>
                        </div>
                        <div className="info-row">
                            <span>{t('account')}:</span>
                            <strong>{scannedData.accountNumber}</strong>
                        </div>
                    </div>

                    <div className="input-group">
                        <label>{t('recipientAccountNumber')} <span className="required">*</span></label>
                        <input
                            type="text"
                            value={scannedData.accountNumber}
                            onChange={e => setScannedData({ ...scannedData, accountNumber: e.target.value })}
                            placeholder={t('enterAccountNumber')}
                            required
                            disabled={loading}
                        />
                    </div>

                    <div className="input-group">
                        <label>{t('amount')} <span className="required">*</span></label>
                        <div className="amount-input-wrapper">
                            <span className="currency">₫</span>
                            <input
                                type="text"
                                value={amount ? parseFloat(amount).toLocaleString('vi-VN') : ''}
                                onChange={e => {
                                    const value = e.target.value.replace(/\./g, '');
                                    setAmount(value ? parseFloat(value) : '');
                                }}
                                placeholder="10.000.000"
                                required
                                disabled={loading}
                            />
                        </div>
                        <small className="helper-text">
                            Available: {account.balance.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                        </small>
                    </div>

                    <div className="input-group">
                        <label>{t('descriptionOptional')}</label>
                        <input
                            type="text"
                            value={description}
                            onChange={e => setDescription(e.target.value)}
                            placeholder={t('whatsThisFor')}
                            maxLength="100"
                            disabled={loading}
                        />
                    </div>

                    <div className="button-group">
                        <button
                            type="button"
                            className="btn-secondary"
                            onClick={() => setScannedData(null)}
                            disabled={loading}
                        >
                            ← {t('backButton')}
                        </button>
                        <button
                            type="submit"
                            className="btn-primary"
                            disabled={loading || !scannedData.accountNumber || !amount}
                        >
                            {loading ? `⏳ ${t('processing')}` : `💰 ${t('sendMoney')}`}
                        </button>
                    </div>
                </form>
            )}
        </div>
    );
};

export default QRCodeScanner;
