const fs = require( 'fs' );
const path = require( 'path' );
const { spawnSync } = require( 'child_process' );

const extra = process.argv.slice( 2 );
const listed = JSON.parse(
	fs.readFileSync( path.join( __dirname, 'paths.json' ), 'utf8' )
);
const targets = extra.length > 0 ? extra : listed;

if ( ! Array.isArray( listed ) ) {
	process.stderr.write( 'tools/js-lint/paths.json must be a JSON array of directories.\n' );
	process.exit( 2 );
}

if ( targets.length === 0 ) {
	process.stdout.write( 'No JS paths to lint.\n' );
	process.exit( 0 );
}

const eslint = path.join( __dirname, 'node_modules', 'eslint', 'bin', 'eslint.js' );
const result = spawnSync(
	process.execPath,
	[ eslint, '--no-error-on-unmatched-pattern', ...targets ],
	{ cwd: __dirname, stdio: 'inherit' }
);

process.exit( result.status ?? 1 );
