const path = require( 'path' );

module.exports = {
	rootDir: path.resolve( __dirname, '../..' ),
	roots: [ path.resolve( __dirname ) ],
	testPathIgnorePatterns: [
		'/node_modules/',
		'/vendor/',
		'/build/',
		'/dist/',
		'\\.min\\.js$',
	],
	passWithNoTests: true,
};
