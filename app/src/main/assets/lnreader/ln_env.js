// LNReader Environment Shim for QuickJS
(function() {
    const deps = (typeof __futon_deps !== 'undefined') ? __futon_deps : {};

    // Console polyfill delegating to native Android logcat
    const makeLog = (level) => (...args) => {
        try {
            const msg = args.map(a => {
                if (a === null) return 'null';
                if (a === undefined) return 'undefined';
                if (typeof a === 'object') {
                    try { return JSON.stringify(a); } catch (_) { return String(a); }
                }
                return String(a);
            }).join(' ');
            if (typeof __futon_native_log === 'function') {
                __futon_native_log(level, msg);
            }
        } catch (_) {}
    };

    globalThis.console = {
        log: makeLog('INFO'),
        info: makeLog('INFO'),
        warn: makeLog('WARN'),
        error: makeLog('ERROR'),
        debug: makeLog('DEBUG'),
        trace: makeLog('DEBUG'),
    };

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

    // Expose globals that plugins frequently access directly
    globalThis.NovelStatus = NovelStatus;
    globalThis.defaultCover = defaultCover;
    globalThis.isUrlAbsolute = isUrlAbsolute;

    // URLSearchParams and URL polyfill
    if (typeof globalThis.URLSearchParams === 'undefined') {
        function URLSearchParams(init) {
            this._params = {};
            if (typeof init === 'string') {
                let s = init;
                if (s.startsWith('?')) s = s.slice(1);
                s.split('&').forEach(pair => {
                    if (!pair) return;
                    const idx = pair.indexOf('=');
                    const k = idx >= 0 ? decodeURIComponent(pair.slice(0, idx)) : decodeURIComponent(pair);
                    const v = idx >= 0 ? decodeURIComponent(pair.slice(idx + 1)) : '';
                    this.append(k, v);
                });
            } else if (init && typeof init === 'object') {
                for (const [k, v] of Object.entries(init)) {
                    this.set(k, String(v));
                }
            }
        }
        URLSearchParams.prototype.get = function(key) {
            const list = this._params[key];
            return list && list.length ? list[0] : null;
        };
        URLSearchParams.prototype.getAll = function(key) {
            return this._params[key] ? [...this._params[key]] : [];
        };
        URLSearchParams.prototype.set = function(key, val) {
            this._params[key] = [String(val)];
        };
        URLSearchParams.prototype.append = function(key, val) {
            if (!this._params[key]) this._params[key] = [];
            this._params[key].push(String(val));
        };
        URLSearchParams.prototype.has = function(key) {
            return Object.prototype.hasOwnProperty.call(this._params, key);
        };
        URLSearchParams.prototype.delete = function(key) {
            delete this._params[key];
        };
        URLSearchParams.prototype.toString = function() {
            const pairs = [];
            for (const [k, values] of Object.entries(this._params)) {
                for (const v of values) {
                    pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
                }
            }
            return pairs.join('&');
        };
        globalThis.URLSearchParams = URLSearchParams;
    }

    if (typeof globalThis.URL === 'undefined') {
        function URL(url, base) {
            let fullUrl = url;
            if (base && !isUrlAbsolute(url)) {
                const cleanBase = base.endsWith('/') ? base.slice(0, -1) : base;
                const cleanUrl = url.startsWith('/') ? url : '/' + url;
                fullUrl = cleanBase + cleanUrl;
            }
            this.href = fullUrl;
            const match = fullUrl.match(/^(https?:)?\/\/([^\/?#]+)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$/i);
            if (match) {
                this.protocol = match[1] || 'https:';
                this.host = match[2] || '';
                this.hostname = this.host.split(':')[0];
                this.port = this.host.split(':')[1] || '';
                this.pathname = match[3] || '/';
                this.search = match[4] ? '?' + match[4] : '';
                this.hash = match[5] ? '#' + match[5] : '';
            } else {
                this.protocol = 'https:';
                this.host = '';
                this.hostname = '';
                this.port = '';
                this.pathname = fullUrl;
                this.search = '';
                this.hash = '';
            }
            this.searchParams = new globalThis.URLSearchParams(this.search);
        }
        URL.prototype.toString = function() {
            return this.href;
        };
        globalThis.URL = URL;
    }

    const fetchApi = async function(url, init) {
        init = init || {};
        const method = (init.method || 'GET').toUpperCase();
        const headers = {
            'Connection': 'keep-alive',
            'Accept': '*/*',
            'Accept-Language': '*',
            'Sec-Fetch-Mode': 'cors',
            'Cache-Control': 'max-age=0',
        };
        if (init.headers) {
            if (typeof init.headers.forEach === 'function') {
                init.headers.forEach((value, key) => { headers[key] = String(value); });
            } else if (Array.isArray(init.headers)) {
                init.headers.forEach(pair => { if (pair && pair.length > 1) headers[pair[0]] = String(pair[1]); });
            } else {
                Object.keys(init.headers).forEach(key => { headers[key] = String(init.headers[key]); });
            }
        }
        const body = (typeof init.body === 'string') ? init.body : (init.body ? JSON.stringify(init.body) : null);
        
        const rawResponse = await __futon_native_fetch(url, method, JSON.stringify(headers), body);
        const parsed = JSON.parse(rawResponse);
        if (parsed.error) {
            throw new Error('Network request failed: ' + parsed.error);
        }
        const responseHeaders = parsed.headers || {};
        
        return {
            url: parsed.url || url,
            status: parsed.status,
            statusText: parsed.statusText || '',
            ok: parsed.status >= 200 && parsed.status < 300,
            text: async () => parsed.body,
            json: async () => JSON.parse(parsed.body),
            headers: {
                get: (k) => responseHeaders[k.toLowerCase()] || null,
                has: (k) => Object.prototype.hasOwnProperty.call(responseHeaders, k.toLowerCase()),
                forEach: (callback) => Object.keys(responseHeaders).forEach(k => callback(responseHeaders[k], k)),
            }
        };
    };

    globalThis.fetch = fetchApi;

    const fetchText = async function(url, init) {
        const res = await fetchApi(url, init);
        return await res.text();
    };

    const fetchProto = async function() {
        throw new Error('Protobuf fetch not implemented in shim');
    };

    const cheerioPkg = {
        load: deps.load,
        default: { load: deps.load },
    };
    cheerioPkg.cheerio = cheerioPkg;

    const htmlparser2Pkg = {
        Parser: deps.Parser,
        default: { Parser: deps.Parser },
    };

    const dayjsPkg = deps.dayjs || {};
    if (!dayjsPkg.default) {
        dayjsPkg.default = dayjsPkg;
    }

    const novelStatusPkg = {
        NovelStatus: NovelStatus,
        default: NovelStatus,
    };

    const fetchPkg = {
        fetchApi: fetchApi,
        fetchText: fetchText,
        fetchFile: fetchApi,
        fetchProto: fetchProto,
        default: {
            fetchApi: fetchApi,
            fetchText: fetchText,
            fetchFile: fetchApi,
            fetchProto: fetchProto,
        },
    };

    const isAbsoluteUrlPkg = {
        isUrlAbsolute: isUrlAbsolute,
        default: isUrlAbsolute,
    };

    const defaultCoverPkg = {
        defaultCover: defaultCover,
        default: defaultCover,
    };

    const FilterTypes = {
        TextInput: 'TextInput',
        Picker: 'Picker',
        Checkbox: 'Checkbox',
        CheckboxGroup: 'Checkbox',
        Switch: 'Switch',
        ExcludableCheckbox: 'ExcludableCheckbox',
        ExcludableCheckboxGroup: 'ExcludableCheckbox',
        Sort: 'Sort',
    };

    const filterInputsPkg = {
        FilterTypes: FilterTypes,
        default: { FilterTypes: FilterTypes },
    };

    const constantsPkg = {
        defaultCover: defaultCover,
        NovelStatus: NovelStatus,
        FilterTypes: FilterTypes,
        default: {
            defaultCover: defaultCover,
            NovelStatus: NovelStatus,
            FilterTypes: FilterTypes,
        },
    };

    const packages = {
        'cheerio': cheerioPkg,
        'htmlparser2': htmlparser2Pkg,
        'dayjs': dayjsPkg,
        '@libs/novelStatus': novelStatusPkg,
        '@libs/fetch': fetchPkg,
        '@libs/isAbsoluteUrl': isAbsoluteUrlPkg,
        '@libs/defaultCover': defaultCoverPkg,
        '@libs/filterInputs': filterInputsPkg,
        '@/types/constants': constantsPkg,
        '@libs/constants': constantsPkg,
        'url': {
            URL: globalThis.URL,
            URLSearchParams: globalThis.URLSearchParams,
            default: { URL: globalThis.URL, URLSearchParams: globalThis.URLSearchParams },
        },
    };

    globalThis.__futon_require = function(pkgName) {
        if (pkgName === '@libs/storage') {
            return {
                storage: {
                    get: (k) => __futon_native_storage_get(k),
                    set: (k, v) => __futon_native_storage_set(k, String(v)),
                },
                localStorage: {
                    get: (k) => __futon_native_storage_get(k),
                    set: (k, v) => __futon_native_storage_set(k, String(v)),
                },
                sessionStorage: {
                    get: (k) => __futon_native_storage_get(k),
                    set: (k, v) => __futon_native_storage_set(k, String(v)),
                },
            };
        }
        if (packages[pkgName]) {
            return packages[pkgName];
        }
        if (pkgName.endsWith('/constants') || pkgName.includes('constants')) {
            return constantsPkg;
        }
        if (pkgName.includes('filterInputs')) {
            return filterInputsPkg;
        }
        if (pkgName.includes('novelStatus')) {
            return novelStatusPkg;
        }
        if (pkgName.includes('defaultCover')) {
            return defaultCoverPkg;
        }
        if (pkgName.includes('fetch')) {
            return fetchPkg;
        }
        console.warn('Unknown package requested: ' + pkgName);
        return {};
    };

    globalThis.__futon_build_default_options = function(plugin) {
        const defaultFilters = {};
        if (plugin) {
            const rawFilters = typeof plugin.filters === 'function' ? plugin.filters() : plugin.filters;
            if (rawFilters && typeof rawFilters === 'object') {
                for (const key in rawFilters) {
                    const filter = rawFilters[key];
                    if (filter && typeof filter === 'object' && typeof filter.value !== 'undefined') {
                        defaultFilters[key] = { value: filter.value };
                    }
                }
            }
        }
        return {
            showLatestNovels: false,
            filters: defaultFilters,
        };
    };

    globalThis.__futon_init_plugin = function(rawCode) {
        const module = { exports: {} };
        const require = globalThis.__futon_require;
        const exports = module.exports;
        
        const fn = new Function('require', 'module', 'exports', rawCode + '; return module.exports.default || module.exports;');
        let pluginInstance = fn(require, module, exports);
        if (typeof pluginInstance === 'function') {
            try {
                pluginInstance = new pluginInstance();
            } catch (_) {
                try {
                    pluginInstance = pluginInstance();
                } catch (e) {
                    console.error('Failed to construct plugin instance: ' + e);
                }
            }
        }
        globalThis.__futon_current_plugin = pluginInstance;
        return !!pluginInstance;
    };
})();
