import { useEffect, useMemo, useRef, useState } from "react";
import { apiRequest } from "./api.js";

const money = (amount) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(Number(amount) || 0);

const MACBOOK_IMAGE = "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=800&q=80";
const SAMSUNG_IMAGE = "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=800&q=80";
const RAM_IMAGE = "https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80";
const IPHONE_IMAGE = "https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=800&q=80";

const PRODUCT_IMAGE_MAP = {
  "89157000000053001": SAMSUNG_IMAGE, // Samsung Galaxy S24 Ultra
  "89157000000083004": MACBOOK_IMAGE, // MacBook Pro M1 Pro
  "89157000000083005": RAM_IMAGE,     // DDR4 RAM
  "89157000000114003": IPHONE_IMAGE,  // iPhone 16 Pro Max
};

function getProductImage(product) {
  const id = product?.productId ? String(product.productId).trim() : "";
  const name = (product?.prodName || "").toLowerCase();

  // 1. Direct ROWID match
  if (id && PRODUCT_IMAGE_MAP[id]) {
    return PRODUCT_IMAGE_MAP[id];
  }

  // 2. Existing valid HTTP product image (if direct image URL from datastore)
  if (product?.prodImage && product.prodImage.trim().startsWith("http") && !product.prodImage.includes("google.com/search")) {
    return product.prodImage.trim();
  }

  // 3. Name-based match for iPhone
  if (name.includes("iphone") || name.includes("16 pro") || name.includes("16 pro max")) {
    return IPHONE_IMAGE;
  }

  // 4. Name-based match for MacBook / Apple M1
  if ((name.includes("macbook") || name.includes("m1") || name.includes("apple") || name.includes("mac") || name.includes("laptop")) && !name.includes("iphone")) {
    return MACBOOK_IMAGE;
  }

  // 5. Name-based match for Samsung S24
  if (name.includes("s24") || name.includes("samsung") || name.includes("galaxy") || name.includes("ultra")) {
    return SAMSUNG_IMAGE;
  }

  // 6. Name-based match for RAM / DDR4
  if (name.includes("ram") || name.includes("ddr4") || name.includes("ddr") || name.includes("memory")) {
    return RAM_IMAGE;
  }

  return "";
}

function isValidEmail(email) {
  if (!email || typeof email !== "string") return false;
  const emailRegex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;
  return emailRegex.test(email.trim());
}

function isValidName(name) {
  if (!name || typeof name !== "string") return false;
  const trimmed = name.trim();
  // Name must not contain numbers and can only contain letters, spaces, hyphens, periods, apostrophes
  return trimmed.length >= 2 && !/\d/.test(trimmed) && /^[a-zA-Z\s.'-]+$/.test(trimmed);
}

function isValidPhone(phone) {
  if (!phone || typeof phone !== "string") return false;
  // Must only contain numbers and be exactly 10 digits
  return /^\d{10}$/.test(phone.trim());
}

function getSavedCart() {
  try {
    const saved = JSON.parse(window.localStorage.getItem("form-field-cart") || "[]");
    return Array.isArray(saved)
      ? saved.filter((item) => item && typeof item.productId === "string" && Number.isInteger(item.quantity) && item.quantity > 0)
      : [];
  } catch {
    return [];
  }
}

function getSavedUser() {
  try {
    const saved = JSON.parse(window.localStorage.getItem("form-field-user") || "null");
    return saved && typeof saved.customerId === "string" ? saved : null;
  } catch {
    return null;
  }
}

function Icon({ name, size = 20 }) {
  const paths = {
    bag: <><path d="M5 8h14l1 13H4L5 8Z" /><path d="M9 9V6a3 3 0 0 1 6 0v3" /></>,
    search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>,
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    close: <><path d="m18 6-12 12M6 6l12 12" /></>,
    plus: <><path d="M12 5v14M5 12h14" /></>,
    minus: <path d="M5 12h14" />,
    check: <path d="m5 12 4 4L19 6" />,
    leaf: <><path d="M20 4c-9 0-15 4-15 11a5 5 0 0 0 5 5c7 0 11-6 10-16Z" /><path d="M5 20c3-5 7-8 12-11" /></>,
    user: <><circle cx="12" cy="8" r="4"/><path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/></>,
    logout: <><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></>,
  };
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {paths[name]}
    </svg>
  );
}

function App() {
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState(getSavedCart);
  const [currentUser, setCurrentUser] = useState(getSavedUser);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("Everything");
  const [cartOpen, setCartOpen] = useState(false);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [checkoutError, setCheckoutError] = useState("");

  // Auth modal states
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authTab, setAuthTab] = useState("login");
  const [loginEmail, setLoginEmail] = useState("");
  const [registerData, setRegisterData] = useState({
    customerName: "",
    email: "",
    phone: "",
    address: "",
  });
  const [authError, setAuthError] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [pendingCheckout, setPendingCheckout] = useState(false);
  const [checkoutAddress, setCheckoutAddress] = useState("");
  const [checkoutPhone, setCheckoutPhone] = useState("");
  const deletingIdsRef = useRef(new Set());

  async function loadProducts() {
    try {
      setLoading(true);
      const items = await apiRequest("/api/products");
      const list = Array.isArray(items) ? items : [];
      const hydrated = list.map((item) => ({
        ...item,
        prodImage: getProductImage(item),
      }));
      setProducts(hydrated);
      setLoadError("");
      return hydrated;
    } catch (error) {
      setLoadError(error.message);
      return [];
    } finally {
      setLoading(false);
    }
  }

  function populateCheckoutFromCustomer(customer) {
    const rawAddr = typeof customer?.address === "string" ? customer.address.trim() : "";
    const rawPhone = customer?.phone != null ? String(customer.phone).trim() : "";
    const defaultAddr = rawAddr && !rawAddr.includes("Please provide") ? rawAddr : "";
    const defaultPh = rawPhone && rawPhone !== "9999999999" ? rawPhone.replace(/\D/g, "").slice(-10) : "";
    setCheckoutAddress(defaultAddr);
    setCheckoutPhone(defaultPh);
    setCheckoutError("");
  }

  function openCheckout(customerOverride) {
    const user = customerOverride || currentUser;
    setCartOpen(false);
    if (!user) {
      setPendingCheckout(true);
      setAuthModalOpen(true);
      setNotice("Please sign in or create an account to checkout.");
      setTimeout(() => setNotice(""), 3500);
      return;
    }
    populateCheckoutFromCustomer(user);
    setCheckoutOpen(true);
  }

  useEffect(() => {
    loadProducts();

    const savedUser = getSavedUser();
    if (savedUser?.customerId) {
      apiRequest(`/api/customers/${encodeURIComponent(savedUser.customerId)}`)
        .then((freshCustomer) => {
          if (freshCustomer && typeof freshCustomer.customerId === "string") {
            setCurrentUser((prev) => {
              if (prev?.customerId !== savedUser.customerId) {
                return prev;
              }
              window.localStorage.setItem("form-field-user", JSON.stringify(freshCustomer));
              return freshCustomer;
            });
          }
        })
        .catch(() => {
          // Keep cached user if backend refresh fails
        });
    }

    const urlParams = new URLSearchParams(window.location.search);
    const checkoutResult = urlParams.get("checkout");
    const orderId = urlParams.get("orderId") || window.localStorage.getItem("form-field-pending-order");

    if (checkoutResult === "success") {
      setNotice("Payment complete — updating inventory...");
      setCart([]);
      window.localStorage.removeItem("form-field-cart");
      window.localStorage.removeItem("form-field-pending-order");
      window.history.replaceState({}, "", window.location.pathname);

      (async () => {
        try {
          if (orderId) {
            await apiRequest(`/api/payments/confirm/${encodeURIComponent(orderId)}`, {
              method: "POST",
            });
          }
        } catch (err) {
          console.warn("Order confirmation notice:", err);
        } finally {
          await loadProducts();
          setNotice("Payment complete — thank you for your order!");
          setTimeout(() => setNotice(""), 3500);
        }
      })();
    } else if (checkoutResult === "cancelled") {
      setNotice("Checkout was cancelled. Your basket is still here.");
      window.localStorage.removeItem("form-field-pending-order");
      window.history.replaceState({}, "", window.location.pathname);
    }
  }, []);

  // Sync cart to local storage for quick reload
  useEffect(() => {
    window.localStorage.setItem("form-field-cart", JSON.stringify(cart));
  }, [cart]);

  // When user is authenticated, fetch their remote Catalyst cart
  useEffect(() => {
    if (currentUser?.customerId && products.length > 0) {
      loadServerCart(currentUser.customerId, products);
    }
  }, [currentUser?.customerId, products.length]);

  async function loadServerCart(customerId, currentProducts) {
    try {
      const res = await apiRequest(`/api/cart/${encodeURIComponent(customerId)}`);
      if (res && res.items) {
        const itemsList = Object.values(res.items).map((item) => {
          const prod = (currentProducts || []).find((p) => p.productId === item.productId);
          return {
            productId: item.productId,
            prodName: item.prodName || prod?.prodName || "Product",
            prodPrice: item.prodPrice ?? prod?.prodPrice ?? 0,
            prodQuantity: prod?.prodQuantity ?? 999,
            prodImage: getProductImage(prod || item),
            quantity: item.quantity,
          };
        });
        setCart(itemsList);
      }
    } catch {
      // In-memory or initial empty cart
    }
  }

  const categories = useMemo(
    () => ["Everything", ...new Set(products.map((product) => product.prodCategory).filter(Boolean))],
    [products],
  );

  const visibleProducts = useMemo(() => {
    const query = search.trim().toLowerCase();
    return products.filter((product) => {
      const matchesCategory = category === "Everything" || product.prodCategory === category;
      const matchesSearch =
        !query ||
        [product.prodName, product.prodDescription, product.prodCategory]
          .filter(Boolean)
          .some((value) => value.toLowerCase().includes(query));
      return product.active !== false && matchesCategory && matchesSearch;
    });
  }, [products, search, category]);

  const itemCount = cart.reduce((total, item) => total + item.quantity, 0);
  const cartTotal = cart.reduce(
    (total, item) => total + Number(item.prodPrice || 0) * item.quantity,
    0,
  );

  // Live cart addition with Catalyst DataStore persistence
  async function addToCart(product) {
    if (product.prodQuantity <= 0) return;
    const existing = cart.find((item) => item.productId === product.productId);
    if (existing && existing.quantity >= product.prodQuantity) {
      setNotice("You’ve reached the available stock for this item.");
      setTimeout(() => setNotice(""), 2600);
      return;
    }

    const newQty = existing ? existing.quantity + 1 : 1;

    setCart((current) => {
      const currentItem = current.find((item) => item.productId === product.productId);
      if (currentItem) {
        return current.map((item) =>
          item.productId === product.productId ? { ...item, quantity: item.quantity + 1 } : item,
        );
      }
      return [...current, { ...product, prodImage: getProductImage(product), quantity: 1 }];
    });
    setNotice(`${product.prodName} added to your bag.`);
    setTimeout(() => setNotice(""), 2600);

    // Live sync to Catalyst DataStore Cartitem table if logged in
    if (currentUser?.customerId) {
      try {
        if (existing) {
          await apiRequest(`/api/cart/${encodeURIComponent(currentUser.customerId)}/items/${encodeURIComponent(product.productId)}`, {
            method: "PUT",
            body: JSON.stringify({ quantity: newQty }),
          });
        } else {
          await apiRequest(`/api/cart/${encodeURIComponent(currentUser.customerId)}/items`, {
            method: "POST",
            body: JSON.stringify({ productId: product.productId, quantity: 1 }),
          });
        }
      } catch (err) {
        console.error("Failed to update Catalyst cart:", err);
      }
    }
  }

  // Live quantity change with Catalyst DataStore persistence
  async function changeQuantity(productId, amount) {
    const item = cart.find((entry) => entry.productId === productId);
    if (!item) return;
    const product = products.find((entry) => entry.productId === productId);
    const newQty = Math.min(item.quantity + amount, product?.prodQuantity ?? item.quantity);

    if (newQty <= 0) {
      removeFromCart(productId);
      return;
    }

    setCart((current) =>
      current.map((entry) =>
        entry.productId === productId ? { ...entry, quantity: newQty } : entry,
      ),
    );

    if (currentUser?.customerId) {
      try {
        await apiRequest(`/api/cart/${encodeURIComponent(currentUser.customerId)}/items/${encodeURIComponent(productId)}`, {
          method: "PUT",
          body: JSON.stringify({ quantity: newQty }),
        });
      } catch (err) {
        console.error("Failed to update Catalyst cart:", err);
      }
    }
  }

  // Live deletion from Catalyst DataStore Cartitem table
  async function removeFromCart(productId) {
    if (!productId) return;
    setCart((current) => current.filter((entry) => entry.productId !== productId));

    if (currentUser?.customerId) {
      if (deletingIdsRef.current.has(productId)) {
        return; // Prevent duplicate in-flight deletion request
      }
      deletingIdsRef.current.add(productId);
      try {
        await apiRequest(`/api/cart/${encodeURIComponent(currentUser.customerId)}/items/${encodeURIComponent(productId)}`, {
          method: "DELETE",
        });
      } catch (err) {
        console.warn("Catalyst cart item removal note:", err?.message || err);
      } finally {
        deletingIdsRef.current.delete(productId);
      }
    }
  }

  // Synchronize entire cart to backend prior to order placement
  async function syncCart(customerId) {
    const serverCart = await apiRequest(`/api/cart/${encodeURIComponent(customerId)}`);
    const serverItems = serverCart?.items || {};
    const desiredIds = new Set(cart.map((item) => item.productId));

    await Promise.all(
      Object.keys(serverItems)
        .filter((productId) => !desiredIds.has(productId))
        .map((productId) =>
          apiRequest(
            `/api/cart/${encodeURIComponent(customerId)}/items/${encodeURIComponent(productId)}`,
            { method: "DELETE" },
          ),
        ),
    );

    for (const item of cart) {
      const itemUrl = `/api/cart/${encodeURIComponent(customerId)}/items/${encodeURIComponent(item.productId)}`;
      if (serverItems[item.productId]) {
        await apiRequest(itemUrl, {
          method: "PUT",
          body: JSON.stringify({ quantity: item.quantity }),
        });
      } else {
        await apiRequest(`/api/cart/${encodeURIComponent(customerId)}/items`, {
          method: "POST",
          body: JSON.stringify({ productId: item.productId, quantity: item.quantity }),
        });
      }
    }
  }

  // Authentication Handlers
  async function handleLogin(e) {
    e.preventDefault();
    const cleanEmail = loginEmail.trim();
    if (!cleanEmail) {
      setAuthError("Please enter your email address.");
      return;
    }
    if (!isValidEmail(cleanEmail)) {
      setAuthError("Please enter a valid email address (e.g. name@example.com).");
      return;
    }

    setAuthBusy(true);
    setAuthError("");
    try {
      const customer = await apiRequest("/api/customers/login", {
        method: "POST",
        body: JSON.stringify({ email: cleanEmail }),
      });
      setCurrentUser(customer);
      window.localStorage.setItem("form-field-user", JSON.stringify(customer));
      setAuthModalOpen(false);
      setNotice(`Welcome back, ${customer.customerName}!`);
      setTimeout(() => setNotice(""), 3000);

      // Persist local cart to customer's account in Catalyst
      await syncCart(customer.customerId);
      await loadServerCart(customer.customerId, products);

      if (pendingCheckout) {
        setPendingCheckout(false);
        openCheckout(customer);
      } else {
        populateCheckoutFromCustomer(customer);
      }
    } catch (err) {
      setAuthError(err.message || "Failed to sign in. Please verify your email.");
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    const cleanName = registerData.customerName.trim();
    const cleanEmail = registerData.email.trim();
    const cleanPhone = registerData.phone.trim();
    const cleanAddress = registerData.address.trim();

    if (!cleanName) {
      setAuthError("Please enter your full name.");
      return;
    }
    if (/\d/.test(cleanName)) {
      setAuthError("Numbers are not allowed in the name field. Please enter letters only.");
      return;
    }
    if (!isValidName(cleanName)) {
      setAuthError("Name can only contain letters, spaces, and hyphens.");
      return;
    }
    if (!cleanEmail) {
      setAuthError("Please enter your email address.");
      return;
    }
    if (!isValidEmail(cleanEmail)) {
      setAuthError("Please enter a valid email address (e.g. name@example.com).");
      return;
    }
    if (!cleanPhone) {
      setAuthError("Please enter your phone number.");
      return;
    }
    if (/\D/.test(cleanPhone)) {
      setAuthError("Phone number cannot contain letters or strings. Only numbers are allowed.");
      return;
    }
    if (cleanPhone.length !== 10) {
      setAuthError("Phone number must be exactly 10 digits.");
      return;
    }
    if (!cleanAddress) {
      setAuthError("Please enter your delivery address.");
      return;
    }

    setAuthBusy(true);
    setAuthError("");
    try {
      const customer = await apiRequest("/api/customers", {
        method: "POST",
        body: JSON.stringify({
          customerName: cleanName,
          email: cleanEmail,
          phone: cleanPhone,
          address: cleanAddress,
        }),
      });
      setCurrentUser(customer);
      window.localStorage.setItem("form-field-user", JSON.stringify(customer));
      setAuthModalOpen(false);
      setNotice(`Welcome, ${customer.customerName}! Your account is ready.`);
      setTimeout(() => setNotice(""), 3000);

      await syncCart(customer.customerId);
      await loadServerCart(customer.customerId, products);

      if (pendingCheckout) {
        setPendingCheckout(false);
        openCheckout(customer);
      } else {
        populateCheckoutFromCustomer(customer);
      }
    } catch (err) {
      setAuthError(err.message || "Registration failed. Please check your information.");
    } finally {
      setAuthBusy(false);
    }
  }

  function handleLogout() {
    setCurrentUser(null);
    window.localStorage.removeItem("form-field-user");
    setCart([]);
    window.localStorage.removeItem("form-field-cart");
    setPendingCheckout(false);
    setCheckoutAddress("");
    setCheckoutPhone("");
    setCheckoutError("");
    setNotice("You have signed out.");
    setTimeout(() => setNotice(""), 2600);
  }

  // Order Placement - STRICTLY GATED TO LOGGED-IN CUSTOMERS
  async function placeOrder(event) {
    event.preventDefault();
    if (!currentUser) {
      setCheckoutError("You must be signed in to place an order.");
      setAuthModalOpen(true);
      return;
    }

    const cleanAddr = checkoutAddress.trim();
    const cleanPh = checkoutPhone.trim();

    if (!cleanAddr) {
      setCheckoutError("Please enter your delivery address.");
      return;
    }
    if (!cleanPh) {
      setCheckoutError("Please enter your 10-digit contact phone number.");
      return;
    }
    if (/\D/.test(cleanPh) || cleanPh.length !== 10) {
      setCheckoutError("Phone number must contain only numbers and be exactly 10 digits.");
      return;
    }

    setCheckoutBusy(true);
    setCheckoutError("");
    try {
      const customerId = currentUser.customerId;

      // Sync updated contact/address back to customer profile in Catalyst
      if (cleanAddr !== currentUser.address || cleanPh !== currentUser.phone) {
        try {
          const updated = await apiRequest(`/api/customers/${encodeURIComponent(customerId)}`, {
            method: "PUT",
            body: JSON.stringify({
              customerName: currentUser.customerName,
              email: currentUser.email,
              phone: cleanPh,
              address: cleanAddr,
            }),
          });
          setCurrentUser(updated);
          window.localStorage.setItem("form-field-user", JSON.stringify(updated));
        } catch (updateErr) {
          console.warn("Could not sync updated contact info to customer profile:", updateErr);
        }
      }

      // Ensure backend cart is completely up to date with bag
      await syncCart(customerId);

      const order = await apiRequest(`/api/orders/${encodeURIComponent(customerId)}`, {
        method: "POST",
        body: JSON.stringify({ customerId }),
      });

      window.localStorage.setItem("form-field-pending-order", order.orderId);

      const payment = await apiRequest(
        `/api/payments/checkout/${encodeURIComponent(order.orderId)}`,
        { method: "POST" },
      );

      if (!payment.checkoutUrl) throw new Error("The payment service did not return a checkout link.");
      window.location.assign(payment.checkoutUrl);
    } catch (error) {
      setCheckoutError(error.message);
      setCheckoutBusy(false);
    }
  }

  return (
    <div className="store">
      <div className="announcement">
        <Icon name="leaf" size={15} />
        <span>Small things, made to make every day better.</span>
        <span className="announcement-extra">Thoughtfully chosen. Made to last.</span>
      </div>

      <header className="site-header">
        <a className="wordmark" href="#" aria-label="Form and Field home">
          <span className="wordmark-symbol">f<span>.</span></span>
          <span className="wordmark-copy">form & field<small>EVERYDAY, CONSIDERED</small></span>
        </a>
        <nav className="main-nav" aria-label="Main navigation">
          <a href="#shop">Shop all</a>
          <a href="#our-way">Our way</a>
          <a href="#footer">Journal</a>
        </nav>

        <div className="header-actions">
          {currentUser ? (
            <div className="user-badge">
              <span className="user-avatar" title={currentUser.email}>
                {currentUser.customerName.charAt(0).toUpperCase()}
              </span>
              <span>{currentUser.customerName}</span>
              <button className="logout-button" onClick={handleLogout} title="Sign out">
                Sign out
              </button>
            </div>
          ) : (
            <button className="auth-button" onClick={() => { setPendingCheckout(false); setAuthTab("login"); setAuthError(""); setAuthModalOpen(true); }}>
              <Icon name="user" size={16} />
              <span>Sign in</span>
            </button>
          )}

          <button className="bag-button" onClick={() => setCartOpen(true)} aria-label={`Open bag, ${itemCount} items`}>
            <Icon name="bag" size={20} />
            <span>Bag</span>
            <span className="bag-count">{itemCount}</span>
          </button>
        </div>
      </header>

      <main>
        <section className="hero">
          <div className="hero-copy">
            <p className="eyebrow"><span className="eyebrow-line" /> A LITTLE MORE INTENTIONAL</p>
            <h1>Make room<br />for <em>the good</em><br />things.</h1>
            <p className="hero-description">
              Everyday essentials with a softer footprint and a little more thought behind them.
            </p>
            <a className="button button-dark hero-cta" href="#shop">
              Find your favorites <Icon name="arrow" size={17} />
            </a>
            <div className="hero-note">
              <span className="hero-note-mark"><Icon name="leaf" size={18} /></span>
              <span>Fewer, better things.<br /><strong>That’s the whole idea.</strong></span>
            </div>
          </div>
          <div className="hero-art" aria-label="A thoughtfully composed still life">
            <div className="art-sun" />
            <div className="art-vase art-vase-back"><span /></div>
            <div className="art-vase art-vase-front"><span /></div>
            <div className="art-book art-book-one" />
            <div className="art-book art-book-two" />
            <div className="art-stem stem-one"><i /><i /><i /></div>
            <div className="art-stem stem-two"><i /><i /></div>
            <div className="art-caption">OBJECTS FOR SLOWER MORNINGS <span>01 / 04</span></div>
          </div>
          <div className="hero-index">EST. WITH INTENTION&nbsp; — &nbsp;VOL. 01</div>
        </section>

        <section className="values-strip" id="our-way">
          <span>GOOD THINGS, WELL MADE</span>
          <span className="value-divider">✳</span>
          <span>LESS, BUT LOVELIER</span>
          <span className="value-divider">✳</span>
          <span>MADE FOR THE EVERYDAY</span>
        </section>

        <section className="shop-section" id="shop">
          <div className="shop-heading">
            <div>
              <p className="eyebrow">THE CONSIDERED COLLECTION</p>
              <h2>Find your <em>everyday.</em></h2>
            </div>
            <div className="shop-heading-right">
              <span className="product-total">{visibleProducts.length} thoughtfully chosen things</span>
              <label className="search-field">
                <Icon name="search" size={17} />
                <input
                  type="search"
                  placeholder="Find something..."
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  aria-label="Search products"
                />
              </label>
            </div>
          </div>

          <div className="category-list" aria-label="Product categories">
            {categories.map((item) => (
              <button
                key={item}
                className={`category-chip${category === item ? " selected" : ""}`}
                onClick={() => setCategory(item)}
              >
                {item}
              </button>
            ))}
          </div>

          {loading ? (
            <div className="state-card"><span className="loader" /> Gathering the good things…</div>
          ) : loadError ? (
            <div className="state-card error-card">
              <span className="state-icon">!</span>
              <h3>We couldn’t reach the shop.</h3>
              <p>{loadError}</p>
              <button className="button button-outline" onClick={() => window.location.reload()}>Try again</button>
            </div>
          ) : visibleProducts.length ? (
            <div className="product-grid">
              {visibleProducts.map((product, index) => (
                <article className="product-card" key={product.productId}>
                  <div className={`product-image image-tone-${index % 4}`}>
                    {getProductImage(product) && (
                      <img
                        src={getProductImage(product)}
                        alt={product.prodName}
                        loading="lazy"
                        referrerPolicy="no-referrer"
                        onError={(event) => {
                          if (!event.currentTarget.dataset.retried) {
                            event.currentTarget.dataset.retried = "1";
                            event.currentTarget.src = "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?auto=format&fit=crop&w=800&q=80";
                          } else {
                            event.currentTarget.style.display = "none";
                          }
                        }}
                      />
                    )}
                    <span className="product-category">{product.prodCategory}</span>
                    {product.prodQuantity <= 0 && <span className="sold-out">Sold out</span>}
                    <button
                      className="quick-add"
                      onClick={() => addToCart(product)}
                      disabled={product.prodQuantity <= 0}
                      aria-label={`Add ${product.prodName} to bag`}
                    >
                      <Icon name="plus" size={18} />
                    </button>
                  </div>
                  <div className="product-info">
                    <div className="product-title-row">
                      <h3>{product.prodName}</h3>
                      <span className="product-price">{money(product.prodPrice)}</span>
                    </div>
                    <p>{product.prodDescription}</p>
                    <span className="stock-note">
                      {product.prodQuantity > 0 ? `${product.prodQuantity} ready to ship` : "Currently unavailable"}
                    </span>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="state-card empty-results">
              <span className="state-icon"><Icon name="search" /></span>
              <h3>Nothing on this shelf just yet.</h3>
              <p>Try another search or browse the full collection.</p>
              <button className="button button-outline" onClick={() => { setSearch(""); setCategory("Everything"); }}>
                See everything
              </button>
            </div>
          )}
        </section>

        <section className="closing-note">
          <span className="closing-flower">✳</span>
          <p>Keep what you love.<br /><em>Choose what lasts.</em></p>
          <span className="closing-caption">A SMALLER, MORE THOUGHTFUL WAY TO SHOP</span>
        </section>
      </main>

      <footer className="site-footer" id="footer">
        <a className="wordmark footer-wordmark" href="#">
          <span className="wordmark-symbol">f<span>.</span></span>
          <span className="wordmark-copy">form & field<small>EVERYDAY, CONSIDERED</small></span>
        </a>
        <span>Thoughtful finds for the everyday.</span>
        <span className="footer-copy">© {new Date().getFullYear()} Form & Field</span>
      </footer>

      {notice && <div className="toast"><Icon name="check" size={17} />{notice}</div>}

      {/* Bag Drawer */}
      {cartOpen && (
        <div className="overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) setCartOpen(false); }}>
          <aside className="drawer" aria-label="Shopping bag">
            <div className="drawer-header">
              <div><p className="eyebrow">YOUR LITTLE COLLECTION</p><h2>Your bag <span>({itemCount})</span></h2></div>
              <button className="icon-button" onClick={() => setCartOpen(false)} aria-label="Close bag"><Icon name="close" /></button>
            </div>
            {cart.length ? (
              <>
                <div className="cart-items">
                  {cart.map((item) => (
                    <div className="cart-item" key={item.productId}>
                      <div className="cart-thumb">
                        {getProductImage(item) && (
                          <img
                            src={getProductImage(item)}
                            alt=""
                            referrerPolicy="no-referrer"
                            onError={(event) => {
                              if (!event.currentTarget.dataset.retried) {
                                event.currentTarget.dataset.retried = "1";
                                event.currentTarget.src = "https://images.unsplash.com/photo-1541807084-5c52b6b3adef?auto=format&fit=crop&w=800&q=80";
                              } else {
                                event.currentTarget.style.display = "none";
                              }
                            }}
                          />
                        )}
                      </div>
                      <div className="cart-item-content">
                        <div className="cart-item-top"><h3>{item.prodName}</h3><span>{money(item.prodPrice * item.quantity)}</span></div>
                        <p>{money(item.prodPrice)} each</p>
                        <div className="cart-item-bottom">
                          <div className="quantity-control">
                            <button onClick={() => changeQuantity(item.productId, -1)} aria-label={`Remove one ${item.prodName}`}><Icon name="minus" size={14} /></button>
                            <span>{item.quantity}</span>
                            <button onClick={() => changeQuantity(item.productId, 1)} aria-label={`Add one ${item.prodName}`} disabled={item.quantity >= item.prodQuantity}><Icon name="plus" size={14} /></button>
                          </div>
                          <button className="remove-link" onClick={() => removeFromCart(item.productId)}>Remove</button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="drawer-bottom">
                  <div className="subtotal-row"><span>Subtotal</span><strong>{money(cartTotal)}</strong></div>
                  <p>Shipping and any applicable taxes are calculated at checkout.</p>
                  <button
                    className="button button-dark checkout-button"
                    onClick={openCheckout}
                  >
                    Continue to checkout <Icon name="arrow" size={17} />
                  </button>
                  <span className="secure-note"><Icon name="leaf" size={15} /> A considered choice, delivered with care.</span>
                </div>
              </>
            ) : (
              <div className="empty-bag">
                <span className="empty-bag-icon"><Icon name="bag" size={28} /></span>
                <h3>A little room for something good.</h3>
                <p>Your bag is taking a well-earned breather.</p>
                <button className="button button-dark" onClick={() => { setCartOpen(false); document.getElementById("shop")?.scrollIntoView({ behavior: "smooth" }); }}>Explore the collection</button>
              </div>
            )}
          </aside>
        </div>
      )}

      {/* Customer Authentication Modal (Login & Register) */}
      {authModalOpen && (
        <div className="overlay modal-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget && !authBusy) { setAuthModalOpen(false); setPendingCheckout(false); } }}>
          <section className="auth-modal" aria-labelledby="auth-modal-title">
            <div className="drawer-header">
              <div>
                <p className="eyebrow">{pendingCheckout ? "CHECKOUT REQUIREMENT" : "WELCOME"}</p>
                <h2 id="auth-modal-title">{authTab === "login" ? "Sign in to your account" : "Create your account"}</h2>
              </div>
              <button className="icon-button" onClick={() => { setAuthModalOpen(false); setPendingCheckout(false); }} disabled={authBusy} aria-label="Close sign in"><Icon name="close" /></button>
            </div>

            {pendingCheckout && (
              <div className="auth-notice">
                <Icon name="leaf" size={16} />
                <span>An account is required to place your order and securely save your bag.</span>
              </div>
            )}

            <div className="auth-tabs">
              <button
                type="button"
                className={`auth-tab ${authTab === "login" ? "active" : ""}`}
                onClick={() => { setAuthTab("login"); setAuthError(""); }}
              >
                Sign in
              </button>
              <button
                type="button"
                className={`auth-tab ${authTab === "register" ? "active" : ""}`}
                onClick={() => { setAuthTab("register"); setAuthError(""); }}
              >
                Create account
              </button>
            </div>

            {authError && <p className="auth-error" role="alert">{authError}</p>}

            {authTab === "login" ? (
              <form className="auth-form" onSubmit={handleLogin} noValidate>
                <label className="form-field full-field">
                  <span>Registered Email</span>
                  <input
                    type="email"
                    required
                    placeholder="you@example.com"
                    value={loginEmail}
                    onChange={(e) => {
                      setLoginEmail(e.target.value);
                      if (authError) setAuthError("");
                    }}
                    autoComplete="email"
                    pattern="^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
                    title="Please enter a valid email address (e.g. name@example.com)"
                    className={loginEmail && !isValidEmail(loginEmail) ? "input-error" : ""}
                  />
                  {loginEmail && !isValidEmail(loginEmail) && (
                    <span className="field-hint error-hint">Must be in email format: name@example.com</span>
                  )}
                </label>
                <button className="button button-dark" type="submit" disabled={authBusy}>
                  {authBusy ? "Verifying..." : "Sign in & continue"}
                </button>
              </form>
            ) : (
              <form className="auth-form" onSubmit={handleRegister} noValidate>
                <label className="form-field">
                  <span>Full name</span>
                  <input
                    required
                    placeholder="Your full name"
                    value={registerData.customerName}
                    onChange={(e) => {
                      const val = e.target.value;
                      if (/\d/.test(val)) {
                        setAuthError("Numbers are not allowed in the name field.");
                      } else if (authError && authError.includes("name")) {
                        setAuthError("");
                      }
                      setRegisterData({ ...registerData, customerName: val });
                    }}
                    autoComplete="name"
                    pattern="^[a-zA-Z\s.'-]+$"
                    title="Name cannot contain numbers"
                    className={registerData.customerName && (/\d/.test(registerData.customerName) || !isValidName(registerData.customerName)) ? "input-error" : ""}
                  />
                  {registerData.customerName && /\d/.test(registerData.customerName) && (
                    <span className="field-hint error-hint">Numbers are not allowed in the name field</span>
                  )}
                </label>
                <label className="form-field">
                  <span>Email address</span>
                  <input
                    type="email"
                    required
                    placeholder="you@example.com"
                    value={registerData.email}
                    onChange={(e) => {
                      setRegisterData({ ...registerData, email: e.target.value });
                      if (authError && authError.includes("email")) setAuthError("");
                    }}
                    autoComplete="email"
                    pattern="^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
                    title="Please enter a valid email address (e.g. name@example.com)"
                    className={registerData.email && !isValidEmail(registerData.email) ? "input-error" : ""}
                  />
                  {registerData.email && !isValidEmail(registerData.email) && (
                    <span className="field-hint error-hint">Must be in email format: name@example.com</span>
                  )}
                </label>
                <label className="form-field">
                  <span>Phone number (10 digits)</span>
                  <input
                    type="tel"
                    required
                    maxLength={10}
                    placeholder="10-digit mobile number"
                    value={registerData.phone}
                    onChange={(e) => {
                      const raw = e.target.value;
                      if (/\D/.test(raw)) {
                        setAuthError("Phone number cannot contain letters or strings. Only numbers are allowed.");
                      } else if (authError && authError.includes("Phone")) {
                        setAuthError("");
                      }
                      const digits = raw.replace(/\D/g, "").slice(0, 10);
                      setRegisterData({ ...registerData, phone: digits });
                    }}
                    autoComplete="tel"
                    pattern="[0-9]{10}"
                    title="Phone number must only contain numbers and be up to 10 digits"
                    className={registerData.phone && registerData.phone.length !== 10 ? "input-error" : ""}
                  />
                  {registerData.phone && registerData.phone.length > 0 && registerData.phone.length < 10 && (
                    <span className="field-hint error-hint">Must be 10 digits ({registerData.phone.length}/10 entered)</span>
                  )}
                  {registerData.phone && registerData.phone.length === 10 && (
                    <span className="field-hint" style={{ color: "#36683e" }}>✓ 10 digits entered</span>
                  )}
                </label>
                <label className="form-field">
                  <span>Delivery address</span>
                  <input
                    required
                    placeholder="Street, City, Postal Code"
                    value={registerData.address}
                    onChange={(e) => {
                      setRegisterData({ ...registerData, address: e.target.value });
                      if (authError) setAuthError("");
                    }}
                    autoComplete="street-address"
                  />
                </label>
                <button className="button button-dark" type="submit" disabled={authBusy}>
                  {authBusy ? "Creating account..." : "Register & continue"}
                </button>
              </form>
            )}
          </section>
        </div>
      )}

      {/* Checkout Modal - Available ONLY to Logged-in Customers */}
      {checkoutOpen && currentUser && (
        <div className="overlay modal-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget && !checkoutBusy) setCheckoutOpen(false); }}>
          <section className="checkout-modal" aria-labelledby="checkout-title">
            <div className="drawer-header">
              <div><p className="eyebrow">ONE LAST THING</p><h2 id="checkout-title">Let’s make it yours.</h2></div>
              <button className="icon-button" onClick={() => setCheckoutOpen(false)} disabled={checkoutBusy} aria-label="Close checkout"><Icon name="close" /></button>
            </div>
            <p className="checkout-intro">Confirm your order details and proceed to secure payment.</p>

            <form className="checkout-form" onSubmit={placeOrder}>
              <div className="customer-summary-card">
                <div><strong>Ordering as:</strong> {currentUser.customerName} ({currentUser.email})</div>
              </div>

              <label className="form-field full-field">
                <span>Delivery address</span>
                <input
                  required
                  placeholder="Street address, City, Postal Code"
                  value={checkoutAddress}
                  onChange={(e) => {
                    setCheckoutAddress(e.target.value);
                    if (checkoutError) setCheckoutError("");
                  }}
                  autoComplete="street-address"
                />
              </label>

              <label className="form-field full-field">
                <span>Contact phone (10 digits)</span>
                <input
                  type="tel"
                  required
                  maxLength={10}
                  placeholder="10-digit mobile number"
                  value={checkoutPhone}
                  onChange={(e) => {
                    const raw = e.target.value.replace(/\D/g, "").slice(0, 10);
                    setCheckoutPhone(raw);
                    if (checkoutError) setCheckoutError("");
                  }}
                  autoComplete="tel"
                  pattern="[0-9]{10}"
                  className={checkoutPhone && checkoutPhone.length !== 10 ? "input-error" : ""}
                />
                {checkoutPhone && checkoutPhone.length > 0 && checkoutPhone.length < 10 && (
                  <span className="field-hint error-hint">Must be 10 digits ({checkoutPhone.length}/10 entered)</span>
                )}
                {checkoutPhone && checkoutPhone.length === 10 && (
                  <span className="field-hint" style={{ color: "#36683e" }}>✓ 10 digits entered</span>
                )}
              </label>

              {checkoutError && <p className="checkout-error" role="alert">{checkoutError}</p>}

              <div className="checkout-total"><span>{itemCount} {itemCount === 1 ? "item" : "items"}</span><strong>{money(cartTotal)}</strong></div>

              <button className="button button-dark checkout-button" type="submit" disabled={checkoutBusy}>
                {checkoutBusy ? <><span className="button-loader" /> Preparing secure checkout…</> : <>Continue to secure payment <Icon name="arrow" size={17} /></>}
              </button>
              <p className="checkout-disclaimer">Your payment is processed securely by Stripe. Your details are only used to fulfil your order.</p>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

export default App;
