import React from 'react';
import { useTranslation } from 'react-i18next';
import './LanguageSwitcher.scss';

const LanguageSwitcher = () => {
    const { i18n } = useTranslation();

    const changeLanguage = (lng) => {
        i18n.changeLanguage(lng);
        localStorage.setItem('language', lng);
    };

    return (
        <div className="language-switcher">
            <button
                className={`lang-btn ${i18n.language === 'en' ? 'active' : ''}`}
                onClick={() => changeLanguage('en')}
                title="English"
            >
                EN
            </button>
            <button
                className={`lang-btn ${i18n.language === 'vi' ? 'active' : ''}`}
                onClick={() => changeLanguage('vi')}
                title="Tiếng Việt"
            >
                VI
            </button>
        </div>
    );
};

export default LanguageSwitcher;
