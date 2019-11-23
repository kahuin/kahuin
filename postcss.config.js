module.exports = {
    plugins: [
        require('autoprefixer'),
        require('tailwindcss'),
        require('postcss-inline-svg')({'paths': ['node_modules/zondicons']}),
        require('cssnano')
    ]
}