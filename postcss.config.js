module.exports = {
    plugins: [
        require('autoprefixer'),
        require('tailwindcss')(
            {
                theme: {
                    screens: {sm: '640px'},
                    colors: {
                        transparent: 'transparent',
                        black: '#000',
                        white: '#fff',
                        gray: {
                            100: '#f7fafc',
                            300: '#e2e8f0',
                            500: '#a0aec0',
                            600: '#718096'
                        },
                        brand: {
                            500: '#ed64a6'
                        }
                    }
                },
                variants: {
                    borderWidth: ['responsive', 'last']
                }
            }),
        require('postcss-inline-svg')({'paths': ['node_modules/zondicons']}),
        require('cssnano')
    ]
}