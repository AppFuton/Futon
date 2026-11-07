// LNReader Environment Shim for QuickJS
(function() {
    const deps = (typeof __futon_deps !== 'undefined') ? __futon_deps : {};

    const NovelStatus = {
        Unknown: 'Unknown',
        Ongoing: 'Ongoing',
        Completed: 'Completed',
        Licensed: 'Licensed',
        PublishingFinished: 'Publishing Finished',
        Cancelled: 'Cancelled',
        OnHiatus: 'On Hiatus',
        STUB: 'STUB',
        Inactive: 'Inactive',
    };

    const isUrlAbsolute = function(url) {
        if (!url) return false;
        if (url.indexOf('//') === 0) return true;
        if (url.indexOf('://') === -1) return false;
        if (url.indexOf('.') === -1) return false;
        if (url.indexOf('/') === -1) return false;
        if (url.indexOf(':') > url.indexOf('/')) return false;
        if (url.indexOf('://') < url.indexOf('.')) return true;
        return false;
    };

    const defaultCover = 'https://github.com/lnreader/lnreader-plugins/blob/master/public/static/coverNotAvailable.webp?raw=true';

    const fetchApi = async function(url, init) {
        init = init || {};
        const method = (init.method || 'GET').toUpperCase();
        const headers = init.headers || {};
        const body = (typeof init.body === 'string') ? init.body : (init.body ? JSON.stringify(init.body) : null);
        
        const rawResponse = await __futon_native_fetch(url, method, JSON.stringify(headers), body);
        const parsed = JSON.parse(rawResponse);
        
        return {
            status: parsed.status,
            ok: parsed.status >= 200 && parsed.status < 300,
            text: async () => parsed.body,
            json: async () => JSON.parse(parsed.body),
            headers: {
                get: (k) => parsed.headers ? parsed.headers[k.toLowerCase()] : null
            }
        };
    };

    const fetchText = async function(url, init) {
        const res = await fetchApi(url, init);
        return await res.text();
    };

    const fetchProto = async function() {
        throw new Error('Protobuf fetch not implemented in shim');
    };

    const packages = {
        'cheerio': { load: deps.load },
        'htmlparser2': { Parser: deps.Parser },
        'dayjs': deps.dayjs,
        '@libs/novelStatus': { NovelStatus: NovelStatus },
        '@libs/fetch': { fetchApi: fetchApi, fetchText: fetchText, fetchProto: fetchProto },
        '@libs/isAbsoluteUrl': { isUrlAbsolute: isUrlAbsolute },
        '@libs/defaultCover': { defaultCover: defaultCover },
        '@libs/filterInputs': { FilterTypes: {} },
    };

    globalThis.__futon_require = function(pkgName) {
        if (pkgName === '@libs/storage') {
            return {
                storage: {
                    get: (k) => __futon_native_storage_get(k),
                    set: (k, v) => __futon_native_storage_set(k, String(v))
                },
                localStorage: {
                    get: (k) => __futon_native_storage_get(k),
                    set: (k, v) => __futon_native_storage_set(k, String(v))
                },
                sessionStorage: {
                    get: (k) => __futon_native_storage_get(k),
                    set: (k, v) => __futon_native_storage_set(k, String(v))
                }
            };
        }
        if (packages[pkgName]) {
            return packages[pkgName];
        }
        console.warn('Unknown package requested: ' + pkgName);
        return {};
    };

    globalThis.__futon_init_plugin = function(rawCode) {
        const module = { exports: {} };
        const require = globalThis.__futon_require;
        const exports = module.exports;
        
        const fn = new Function('require', 'module', 'exports', rawCode + '; return module.exports.default || module.exports;');
        const pluginInstance = fn(require, module, exports);
        globalThis.__futon_current_plugin = pluginInstance;
        return !!pluginInstance;
    };
})();

