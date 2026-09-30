import { useEffect, useMemo, useRef, useState, type RefObject, type SubmitEvent } from "react";
import type { HotelCommands } from "../application/HotelCommands";
import type { HotelQueries } from "../application/HotelQueries";
import { hasValidGuestDetails, isValidEmail, validateSearch } from "../domain/booking";
import { addDays, formatDate, formatMoney, getNights, localDateOffset } from "../date";
import type {
  GuestDetails,
  Hotel,
  Reservation,
  RoomOffer,
  RoomTypeDraft,
  SearchFormValues,
  SearchResponse,
  SearchStay,
} from "../types";

type Page = "search" | "results" | "details" | "checkout" | "confirmation" | "bookings" | "staff";
type ResultState = "idle" | "loading" | "error" | "empty" | "success";

const responsiveImagePaths: Readonly<Record<string, string>> = {
  "/images/four-seasons-ritz-lisboa.webp": "/images/four-seasons-ritz-lisboa-640.webp",
  "/images/pestana-palace-lisboa.webp": "/images/pestana-palace-lisboa-640.webp",
};

function getResponsiveImageSources(imagePath: string): string | undefined {
  const imagePathWithoutFragment = imagePath.split(/[?#]/, 1)[0];
  const smallImagePath = responsiveImagePaths[imagePathWithoutFragment];
  return smallImagePath ? `${smallImagePath} 640w, ${imagePath} 1200w` : undefined;
}

const initialSearch = (): SearchFormValues => {
  const checkIn = localDateOffset(45);
  return { destination: "Lisbon, Portugal", checkIn, checkOut: addDays(checkIn, 4), guests: "2" };
};

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return "The request could not be completed. Try again.";
}

function minimumRate(room: RoomOffer): number {
  return room.nightlyRates.reduce((lowest, rate) => Math.min(lowest, rate.amount), Number.POSITIVE_INFINITY);
}

function resultsMessage(state: ResultState, count: number): string {
  if (state === "success") return `${count} stays to compare`;
  if (state === "loading") return "Checking availability";
  return "Search results";
}

function saveLabel(saving: boolean, isNew: boolean): string {
  if (saving) return "Saving…";
  return isNew ? "Add room type" : "Save room details";
}

interface HotelAppProps {
  readonly queries: HotelQueries;
  readonly commands: HotelCommands;
}

export function HotelApp({ queries, commands }: HotelAppProps) {
  const [view, setView] = useState<Page>("search");
  const [search, setSearch] = useState<SearchFormValues>(initialSearch);
  const [searchError, setSearchError] = useState("");
  const [resultError, setResultError] = useState("");
  const [resultState, setResultState] = useState<ResultState>("idle");
  const [searchResult, setSearchResult] = useState<SearchResponse | null>(null);
  const [sortBy, setSortBy] = useState("recommended");
  const [activeStayId, setActiveStayId] = useState("");
  const [selectedRoomId, setSelectedRoomId] = useState("");
  const [guest, setGuest] = useState<GuestDetails>({ name: "", email: "" });
  const [checkoutError, setCheckoutError] = useState("");
  const [checkoutAttempted, setCheckoutAttempted] = useState(false);
  const [policyAccepted, setPolicyAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [confirmedBooking, setConfirmedBooking] = useState<Reservation | null>(null);
  const [bookings, setBookings] = useState<Reservation[]>([]);
  const [historyEmail, setHistoryEmail] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [historyLoading, setHistoryLoading] = useState(false);
  const [pendingCancel, setPendingCancel] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState("");
  const [adminKey, setAdminKey] = useState("");
  const pageHeadingRef = useRef<HTMLHeadingElement>(null);
  const cancelDialogRef = useRef<HTMLDialogElement>(null);
  const bookingIdRef = useRef("");
  const previousViewRef = useRef(view);

  const orderedStays = useMemo(() => {
    const stays = [...(searchResult?.stays ?? [])];
    if (sortBy === "price") {
      return stays.sort((first, second) => minimumRate(first.rooms[0]) - minimumRate(second.rooms[0]));
    }
    if (sortBy === "rating") return stays.sort((first, second) => second.rating - first.rating);
    return stays;
  }, [searchResult, sortBy]);

  const activeStay = searchResult?.stays.find((stay) => stay.id === activeStayId) ?? null;
  const selectedRoom = activeStay?.rooms.find((room) => room.id === selectedRoomId) ?? activeStay?.rooms[0] ?? null;
  const cancelTarget = bookings.find((booking) => booking.id === pendingCancel) ?? null;
  const exploreCurrent = ["search", "results", "details", "checkout", "confirmation"].includes(view);

  useEffect(() => {
    if (previousViewRef.current !== view) {
      previousViewRef.current = view;
      pageHeadingRef.current?.focus();
    }
  }, [view]);

  useEffect(() => {
    const dialog = cancelDialogRef.current;
    if (!dialog) return;
    if (pendingCancel && !dialog.open) {
      if (typeof dialog.showModal === "function") dialog.showModal();
      else dialog.setAttribute("open", "");
    }
    if (!pendingCancel && dialog.open) {
      if (typeof dialog.close === "function") dialog.close();
      else dialog.removeAttribute("open");
    }
  }, [pendingCancel]);

  function changeSearch(key: keyof SearchFormValues, value: string) {
    setSearch((previous) => {
      const next = { ...previous, [key]: value };
      if (key === "checkIn" && next.checkOut <= value) next.checkOut = addDays(value, 1);
      return next;
    });
    setSearchError("");
  }

  async function runSearch(event?: SubmitEvent<HTMLFormElement>) {
    event?.preventDefault();
    const validationError = validateSearch(search);
    if (validationError) {
      setSearchError(validationError);
      return;
    }
    setView("results");
    setResultState("loading");
    setResultError("");
    try {
      const result = await queries.searchStays({
        destination: search.destination.trim(),
        checkIn: search.checkIn,
        checkOut: search.checkOut,
        guests: Number(search.guests),
      });
      setSearchResult(result);
      setResultState(result.stays.length ? "success" : "empty");
      if (result.stays.length) {
        setActiveStayId(result.stays[0].id);
        setSelectedRoomId(result.stays[0].rooms[0]?.id ?? "");
      }
    } catch (error) {
      setResultError(errorMessage(error));
      setResultState("error");
    }
  }

  function selectStay(stay: SearchStay) {
    setActiveStayId(stay.id);
    setSelectedRoomId(stay.rooms[0]?.id ?? "");
    setView("details");
  }

  function continueToCheckout() {
    bookingIdRef.current = crypto.randomUUID();
    setCheckoutError("");
    setCheckoutAttempted(false);
    setPolicyAccepted(false);
    setView("checkout");
  }

  function updateGuest(key: keyof GuestDetails, value: string) {
    setGuest((previous) => ({ ...previous, [key]: value }));
    setCheckoutError("");
  }

  async function submitBooking(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setCheckoutAttempted(true);
    const emailIsValid = isValidEmail(guest.email);
    if (!hasValidGuestDetails(guest, policyAccepted) || !activeStay || !selectedRoom) {
      setCheckoutError("Add a valid name and email, then accept the room terms to continue.");
      if (!guest.name.trim()) document.getElementById("guest-name")?.focus();
      else if (!emailIsValid) document.getElementById("guest-email")?.focus();
      else if (!policyAccepted) document.getElementById("policy-check")?.focus();
      return;
    }
    setSubmitting(true);
    setCheckoutError("");
    const reservationId = bookingIdRef.current || crypto.randomUUID();
    bookingIdRef.current = reservationId;
    try {
      const booking = await commands.createReservation({
        reservationId,
        hotelId: activeStay.id,
        roomTypeId: selectedRoom.id,
        checkIn: search.checkIn,
        checkOut: search.checkOut,
        rooms: 1,
        guests: Number(search.guests),
        guestName: guest.name.trim(),
        guestEmail: guest.email.trim(),
      });
      bookingIdRef.current = "";
      setConfirmedBooking(booking);
      setBookings((previous) => [booking, ...previous.filter((item) => item.id !== booking.id)]);
      setHistoryEmail(guest.email.trim());
      setView("confirmation");
    } catch (error) {
      setCheckoutError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function loadBookings(email: string) {
    if (!email.trim()) {
      setHistoryError("Enter the email address used for the reservation.");
      return;
    }
    setHistoryLoading(true);
    setHistoryError("");
    try {
      const result = await queries.findReservationsByEmail(email.trim());
      setBookings(result);
      setHistoryEmail(email.trim());
    } catch (error) {
      setHistoryError(errorMessage(error));
    } finally {
      setHistoryLoading(false);
    }
  }

  async function confirmCancellation() {
    if (!pendingCancel) return;
    setCancelError("");
    try {
      const cancelled = await commands.cancelReservation(pendingCancel);
      setBookings((previous) => previous.map((booking) => booking.id === cancelled.id ? cancelled : booking));
      setConfirmedBooking((previous) => previous?.id === cancelled.id ? cancelled : previous);
      setPendingCancel(null);
    } catch (error) {
      setCancelError(errorMessage(error));
    }
  }

  function showExplore() {
    setSearchError("");
    setView("search");
  }

  function showBookings() {
    setHistoryError("");
    setView("bookings");
    if (historyEmail) void loadBookings(historyEmail);
  }

  return (
    <>
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="site-header shell">
        <a className="wordmark" href="#main-content" onClick={(event) => { event.preventDefault(); showExplore(); }}>
          <span className="wordmark-main">STAYS</span>
          <span className="wordmark-sub">Lisbon reservations</span>
        </a>
        <nav className="main-nav" aria-label="Main navigation">
          <button className="nav-button" type="button" aria-current={exploreCurrent ? "page" : undefined} onClick={showExplore}>Explore</button>
          <button className="nav-button" type="button" aria-current={view === "bookings" ? "page" : undefined} onClick={showBookings}>My bookings{bookings.length ? ` (${bookings.length})` : ""}</button>
          <button className="nav-button" type="button" aria-current={view === "staff" ? "page" : undefined} onClick={() => setView("staff")}>Staff</button>
        </nav>
      </header>
      <div className="shell">
        <div className="prototype-note" role="note">
          <span className="note-label">Live demo</span>
          <span>Availability and nightly prices come from the reservation services. Demo payments are simulated.</span>
        </div>
      </div>
      <main id="main-content" className="shell">
        <HotelPageContent
          queries={queries}
          commands={commands}
          view={view}
          search={search}
          searchError={searchError}
          resultError={resultError}
          resultState={resultState}
          searchResult={searchResult}
          orderedStays={orderedStays}
          sortBy={sortBy}
          onSortChange={setSortBy}
          activeStay={activeStay}
          selectedRoom={selectedRoom}
          onSelectedRoomChange={setSelectedRoomId}
          onPolicyChange={setPolicyAccepted}
          guest={guest}
          checkoutError={checkoutError}
          checkoutAttempted={checkoutAttempted}
          policyAccepted={policyAccepted}
          submitting={submitting}
          confirmedBooking={confirmedBooking}
          bookings={bookings}
          historyEmail={historyEmail}
          historyError={historyError}
          historyLoading={historyLoading}
          cancelError={cancelError}
          adminKey={adminKey}
          onAdminKeyChange={setAdminKey}
          pageHeadingRef={pageHeadingRef}
          onSearchChange={changeSearch}
          onNavigate={setView}
          onRunSearch={runSearch}
          onShowExplore={showExplore}
          onSelectStay={selectStay}
          onContinueToCheckout={continueToCheckout}
          onGuestChange={updateGuest}
          onSubmitBooking={submitBooking}
          onShowBookings={showBookings}
          onHistoryEmailChange={setHistoryEmail}
          onLoadBookings={loadBookings}
          onCancelBooking={setPendingCancel}
        />
      </main>
      <footer className="site-footer shell">
        <p>Photography: “Hotel Ritz Lisboa 5529” by Manuelvbotelho, <a href="https://creativecommons.org/licenses/by-sa/3.0/" target="_blank" rel="noreferrer">CC BY-SA 3.0</a>; “Palácio Vale Flor (Pestana Palace Hotel)” by Portuguese_eyes / Vitor Oliveira, <a href="https://creativecommons.org/licenses/by-sa/2.0/" target="_blank" rel="noreferrer">CC BY-SA 2.0</a>. <a href="https://commons.wikimedia.org/wiki/File:Hotel_Ritz_Lisboa_5529.jpg" target="_blank" rel="noreferrer">Ritz photo source</a> · <a href="https://commons.wikimedia.org/wiki/File:Pal%C3%A1cio_Vale_Flor_%28_Pestana_Palace_Hotel_%29_-_Lisboa_-_Portugal_%2852340698909%29.jpg" target="_blank" rel="noreferrer">Pestana photo source</a>.</p>
        <p>Hotel reservations · local demo</p>
      </footer>
      <dialog
        ref={cancelDialogRef}
        aria-labelledby="cancel-dialog-title"
        aria-describedby="cancel-dialog-copy"
        onCancel={(event) => { event.preventDefault(); setPendingCancel(null); }}
        onClose={() => setPendingCancel(null)}
      >
        {cancelTarget ? (
          <>
            <p className="eyebrow">Local demo only</p>
            <h2 id="cancel-dialog-title">Cancel this reservation?</h2>
            <p id="cancel-dialog-copy">This releases the room and records a demo refund for {cancelTarget.hotelName}.</p>
            <div className="dialog-actions">
              <button className="button button-secondary" type="button" autoFocus onClick={() => setPendingCancel(null)}>Keep booking</button>
              <button className="button button-danger" type="button" onClick={() => void confirmCancellation()}>Cancel reservation</button>
            </div>
          </>
        ) : null}
      </dialog>
    </>
  );
}

interface HotelPageContentProps {
  readonly queries: HotelQueries;
  readonly commands: HotelCommands;
  readonly view: Page;
  readonly search: SearchFormValues;
  readonly searchError: string;
  readonly resultError: string;
  readonly resultState: ResultState;
  readonly searchResult: SearchResponse | null;
  readonly orderedStays: SearchStay[];
  readonly sortBy: string;
  readonly onSortChange: (value: string) => void;
  readonly activeStay: SearchStay | null;
  readonly selectedRoom: RoomOffer | null;
  readonly onSelectedRoomChange: (value: string) => void;
  readonly onPolicyChange: (accepted: boolean) => void;
  readonly guest: GuestDetails;
  readonly checkoutError: string;
  readonly checkoutAttempted: boolean;
  readonly policyAccepted: boolean;
  readonly submitting: boolean;
  readonly confirmedBooking: Reservation | null;
  readonly bookings: Reservation[];
  readonly historyEmail: string;
  readonly historyError: string;
  readonly historyLoading: boolean;
  readonly cancelError: string;
  readonly adminKey: string;
  readonly onAdminKeyChange: (value: string) => void;
  readonly pageHeadingRef: RefObject<HTMLHeadingElement | null>;
  readonly onSearchChange: (key: keyof SearchFormValues, value: string) => void;
  readonly onNavigate: (view: Page) => void;
  readonly onRunSearch: (event?: SubmitEvent<HTMLFormElement>) => Promise<void>;
  readonly onShowExplore: () => void;
  readonly onSelectStay: (stay: SearchStay) => void;
  readonly onContinueToCheckout: () => void;
  readonly onGuestChange: (key: keyof GuestDetails, value: string) => void;
  readonly onSubmitBooking: (event: SubmitEvent<HTMLFormElement>) => Promise<void>;
  readonly onShowBookings: () => void;
  readonly onHistoryEmailChange: (value: string) => void;
  readonly onLoadBookings: (email: string) => Promise<void>;
  readonly onCancelBooking: (id: string) => void;
}

type SearchPageProps = Pick<
  HotelPageContentProps,
  "search" | "searchError" | "onSearchChange" | "onRunSearch" | "pageHeadingRef"
>;
type ResultsPageProps = Pick<
  HotelPageContentProps,
  | "search"
  | "resultState"
  | "searchResult"
  | "orderedStays"
  | "sortBy"
  | "onSortChange"
  | "resultError"
  | "pageHeadingRef"
  | "onRunSearch"
  | "onShowExplore"
  | "onSelectStay"
>;
type ResultsHeadingProps = Pick<
  HotelPageContentProps,
  "search" | "pageHeadingRef" | "resultState" | "orderedStays" | "sortBy" | "onSortChange"
> & { readonly city: string };
type ResultStatePanelProps = Pick<
  HotelPageContentProps,
  "resultState" | "resultError" | "onRunSearch" | "onShowExplore"
>;
type AvailableStaysProps = Pick<HotelPageContentProps, "orderedStays" | "onSelectStay">;
type StayDetailsPageProps = Pick<
  HotelPageContentProps,
  "onNavigate" | "search" | "pageHeadingRef" | "onSelectedRoomChange" | "onContinueToCheckout"
> & { readonly stay: SearchStay; readonly room: RoomOffer };
type CheckoutPageProps = Pick<
  HotelPageContentProps,
  | "onNavigate"
  | "search"
  | "pageHeadingRef"
  | "onSubmitBooking"
  | "guest"
  | "onGuestChange"
  | "checkoutAttempted"
  | "policyAccepted"
  | "onPolicyChange"
  | "checkoutError"
  | "submitting"
> & { readonly stay: SearchStay; readonly room: RoomOffer };
type ConfirmationPageProps = Pick<
  HotelPageContentProps,
  "search" | "pageHeadingRef" | "onShowBookings"
> & { readonly booking: Reservation };
type BookingsPageProps = Pick<
  HotelPageContentProps,
  | "pageHeadingRef"
  | "bookings"
  | "historyEmail"
  | "historyError"
  | "historyLoading"
  | "cancelError"
  | "onLoadBookings"
  | "onHistoryEmailChange"
  | "onCancelBooking"
  | "onShowExplore"
>;

function HotelPageContent(props: HotelPageContentProps) {
  switch (props.view) {
    case "search":
      return <SearchPage
        search={props.search}
        searchError={props.searchError}
        onSearchChange={props.onSearchChange}
        onRunSearch={props.onRunSearch}
        pageHeadingRef={props.pageHeadingRef}
      />;
    case "results":
      return <ResultsPage
        search={props.search}
        resultState={props.resultState}
        searchResult={props.searchResult}
        orderedStays={props.orderedStays}
        sortBy={props.sortBy}
        onSortChange={props.onSortChange}
        resultError={props.resultError}
        pageHeadingRef={props.pageHeadingRef}
        onRunSearch={props.onRunSearch}
        onShowExplore={props.onShowExplore}
        onSelectStay={props.onSelectStay}
      />;
    case "details":
      return props.activeStay && props.selectedRoom ? <StayDetailsPage
        search={props.search}
        pageHeadingRef={props.pageHeadingRef}
        onNavigate={props.onNavigate}
        onSelectedRoomChange={props.onSelectedRoomChange}
        onContinueToCheckout={props.onContinueToCheckout}
        stay={props.activeStay}
        room={props.selectedRoom}
      /> : null;
    case "checkout":
      return props.activeStay && props.selectedRoom ? <CheckoutPage
        search={props.search}
        pageHeadingRef={props.pageHeadingRef}
        onNavigate={props.onNavigate}
        onSubmitBooking={props.onSubmitBooking}
        guest={props.guest}
        onGuestChange={props.onGuestChange}
        checkoutAttempted={props.checkoutAttempted}
        policyAccepted={props.policyAccepted}
        onPolicyChange={props.onPolicyChange}
        checkoutError={props.checkoutError}
        submitting={props.submitting}
        stay={props.activeStay}
        room={props.selectedRoom}
      /> : null;
    case "confirmation":
      return props.confirmedBooking ? <ConfirmationPage
        search={props.search}
        pageHeadingRef={props.pageHeadingRef}
        onShowBookings={props.onShowBookings}
        booking={props.confirmedBooking}
      /> : null;
    case "bookings":
      return <BookingsPage
        pageHeadingRef={props.pageHeadingRef}
        bookings={props.bookings}
        historyEmail={props.historyEmail}
        historyError={props.historyError}
        historyLoading={props.historyLoading}
        cancelError={props.cancelError}
        onLoadBookings={props.onLoadBookings}
        onHistoryEmailChange={props.onHistoryEmailChange}
        onCancelBooking={props.onCancelBooking}
        onShowExplore={props.onShowExplore}
      />;
    case "staff":
      return <StaffPage
        queries={props.queries}
        commands={props.commands}
        adminKey={props.adminKey}
        onAdminKeyChange={props.onAdminKeyChange}
        headingRef={props.pageHeadingRef}
      />;
    default:
      return null;
  }
}

function SearchPage(props: SearchPageProps) {
  return (
    <section className="view" aria-labelledby="search-title">
      <div className="search-hero">
        <div className="hero-copy">
          <p className="eyebrow"><span className="eyebrow-mark" />Lisbon, Portugal · a short list of stays</p>
          <h1 id="search-title" tabIndex={-1} ref={props.pageHeadingRef}>Find a stay that feels like Lisbon.</h1>
          <p className="lede">Compare thoughtful addresses, room choices and live nightly rates before you book.</p>
        </div>
        <figure className="hero-photo">
          <img src="/images/pestana-palace-lisboa.webp" srcSet={getResponsiveImageSources("/images/pestana-palace-lisboa.webp")} sizes="(max-width: 760px) min(100vw, 620px), 46vw" width="1200" height="800" alt="Pestana Palace Lisboa, a pale green historic palace framed by trees." fetchPriority="high" />
          <figcaption className="photo-caption"><strong>Pestana Palace Lisboa</strong><span>Alcântara · Lisbon</span></figcaption>
        </figure>
      </div>
      <SearchForm values={props.search} error={props.searchError} onChange={props.onSearchChange} onSubmit={props.onRunSearch} />
      <p className="search-footnote"><span className="eyebrow-mark" /><span><strong>Two Lisbon stays to begin.</strong> Choose dates and guests to see nightly rates and available room types.</span></p>
    </section>
  );
}

function ResultsPage(props: ResultsPageProps) {
  const city = props.search.destination.split(",")[0];
  return (
    <section className="view" aria-labelledby="results-title">
      <ResultsHeading
        search={props.search}
        pageHeadingRef={props.pageHeadingRef}
        resultState={props.resultState}
        orderedStays={props.orderedStays}
        sortBy={props.sortBy}
        onSortChange={props.onSortChange}
        city={city}
      />
      <div className="split-heading">
        <button className="back-button" type="button" onClick={props.onShowExplore}>← Edit search</button>
        <p className="result-count">{props.search.guests} adults · {getNights(props.search.checkIn, props.search.checkOut)} nights</p>
      </div>
      <ResultStatePanel
        resultState={props.resultState}
        resultError={props.resultError}
        onRunSearch={props.onRunSearch}
        onShowExplore={props.onShowExplore}
      />
      {props.resultState === "success" && props.searchResult ? <AvailableStays orderedStays={props.orderedStays} onSelectStay={props.onSelectStay} /> : null}
    </section>
  );
}

function ResultsHeading(props: ResultsHeadingProps) {
  return (
    <div className="section-topline">
      <div className="section-copy">
        <p className="eyebrow">{props.search.destination} · {formatDate(props.search.checkIn, { day: "numeric", month: "short" })} – {formatDate(props.search.checkOut)} · {props.search.guests} guests</p>
        <h2 id="results-title" tabIndex={-1} ref={props.pageHeadingRef}>Stays in {props.city}</h2>
      </div>
      <div className="result-tools">
        <output className="result-count" aria-live="polite">{resultsMessage(props.resultState, props.orderedStays.length)}</output>
        {props.resultState === "success" ? (
          <div className="field sort-field">
            <label htmlFor="sort-stays" className="field-label">Sort stays</label>
            <select id="sort-stays" className="input-control" value={props.sortBy} onChange={(event) => props.onSortChange(event.target.value)}>
              <option value="recommended">Recommended</option>
              <option value="rating">Guest score</option>
              <option value="price">Nightly price</option>
            </select>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function ResultStatePanel(props: ResultStatePanelProps) {
  if (props.resultState === "loading") return <LoadingResults />;
  if (props.resultState === "error") {
    return (
      <div className="state-panel error-panel" role="alert">
        <p className="eyebrow">Search interrupted</p>
        <h3>We couldn’t check availability.</h3>
        <p>{props.resultError}</p>
        <button className="button button-secondary" type="button" onClick={() => void props.onRunSearch()}>Try search again</button>
      </div>
    );
  }
  if (props.resultState === "empty") {
    return (
      <div className="state-panel" aria-live="polite">
        <p className="eyebrow">No matching stays</p>
        <h3>No rooms are available for these dates.</h3>
        <p>Try a different destination or adjust your dates to see more options.</p>
        <button className="button button-primary" type="button" onClick={props.onShowExplore}>Change search</button>
      </div>
    );
  }
  return null;
}

function AvailableStays(props: AvailableStaysProps) {
  return (
    <>
      <p className="demo-detail-note"><strong>Clear pricing.</strong> Nightly rates can change by date. Your total is shown before you confirm.</p>
      <div className="stay-grid">
        {props.orderedStays.map((stay) => <StayCard key={stay.id} stay={stay} onSelect={props.onSelectStay} />)}
      </div>
    </>
  );
}

function StayDetailsPage(props: StayDetailsPageProps) {
  return (
    <section className="view" aria-labelledby="hotel-title">
      <div className="split-heading">
        <button className="back-button" type="button" onClick={() => props.onNavigate("results")}>← Back to results</button>
        <p className="result-count">{formatDate(props.search.checkIn, { day: "numeric", month: "short" })} – {formatDate(props.search.checkOut)}</p>
      </div>
      <div className="detail-layout">
        <div className="detail-main">
          <img className="detail-image" src={props.stay.imagePath} srcSet={getResponsiveImageSources(props.stay.imagePath)} sizes="(max-width: 760px) 100vw, 58vw" width="1200" height="800" alt={props.stay.imageAlt} />
          <div className="detail-title-row">
            <div>
              <p className="stay-place">{props.stay.district} · {props.stay.city}</p>
              <h1 id="hotel-title" tabIndex={-1} ref={props.pageHeadingRef}>{props.stay.name}</h1>
            </div>
            <div className="stay-score"><span className="score-chip">{props.stay.rating.toFixed(1)}</span><span>guest score / 10</span></div>
          </div>
          <p className="detail-description">{props.stay.summary}</p>
          <div className="facts-row" aria-label="Stay overview">
            <span><strong>{props.search.guests} guests</strong> · one room</span>
            <span><strong>{getNights(props.search.checkIn, props.search.checkOut)} nights</strong> · {formatDate(props.search.checkIn, { day: "numeric", month: "short" })} to {formatDate(props.search.checkOut, { day: "numeric", month: "short" })}</span>
            <span>{props.stay.address}</span>
          </div>
          <div>
            <h2 className="section-heading" id="room-choices-heading">Choose a room</h2>
            <p className="section-subcopy">Rates are shown for your selected dates. Reserve by room type.</p>
            <fieldset className="room-list" aria-labelledby="room-choices-heading">
              <legend className="sr-only">Available room choices</legend>
              {props.stay.rooms.map((room) => (
                <label className={`room-option${props.room.id === room.id ? " selected" : ""}`} key={room.id}>
                  <input type="radio" name="room-choice" value={room.id} checked={props.room.id === room.id} onChange={() => props.onSelectedRoomChange(room.id)} />
                  <span className="sr-only">{room.name} room option</span>
                  <span className="room-copy"><strong>{room.name}</strong><span>{room.details}</span><small>{room.availableRooms} rooms available · Cancellation terms vary by property</small></span>
                  <span className="room-rate"><strong>{formatMoney(minimumRate(room))}</strong><span>from / night</span></span>
                </label>
              ))}
            </fieldset>
          </div>
        </div>
        <BookingSummary stay={props.stay} room={props.room} search={props.search} onContinue={props.onContinueToCheckout} />
      </div>
    </section>
  );
}

function CheckoutPage(props: CheckoutPageProps) {
  return (
    <section className="view" aria-labelledby="checkout-title">
      <div className="split-heading">
        <button className="back-button" type="button" onClick={() => props.onNavigate("details")}>← Back to room choices</button>
        <p className="result-count">Guest details · demo payment</p>
      </div>
      <div className="checkout-layout">
        <div className="checkout-main">
          <p className="eyebrow">One more step</p>
          <h1 className="checkout-title" id="checkout-title" tabIndex={-1} ref={props.pageHeadingRef}>Guest details</h1>
          <p className="checkout-intro">Add the lead guest’s contact details. No card information is requested in this local demo.</p>
          <form className="checkout-form" onSubmit={props.onSubmitBooking} noValidate>
            <div className="field">
              <label htmlFor="guest-name">Full name</label>
              <input className="input-control" id="guest-name" autoComplete="name" value={props.guest.name} onChange={(event) => props.onGuestChange("name", event.target.value)} aria-describedby="guest-name-hint" aria-invalid={props.checkoutAttempted && !props.guest.name.trim()} required />
              <p className="field-hint" id="guest-name-hint">Name of the person checking in.</p>
            </div>
            <div className="field">
              <label htmlFor="guest-email">Email address</label>
              <input className="input-control" id="guest-email" type="email" autoComplete="email" value={props.guest.email} onChange={(event) => props.onGuestChange("email", event.target.value)} aria-describedby="guest-email-hint" aria-invalid={props.checkoutAttempted && !isValidEmail(props.guest.email)} required />
              <p className="field-hint" id="guest-email-hint">Used to find this reservation in My bookings.</p>
            </div>
            <label className="checkbox-line">
              <input id="policy-check" type="checkbox" checked={props.policyAccepted} onChange={(event) => props.onPolicyChange(event.target.checked)} aria-invalid={props.checkoutAttempted && !props.policyAccepted} required />
              <span>I’ve reviewed the room details and the cancellation terms for this stay.</span>
            </label>
            {props.checkoutError ? <p className="checkout-error" role="alert">{props.checkoutError}</p> : null}
            <button className="button button-primary" type="submit" disabled={props.submitting}>{props.submitting ? "Confirming reservation…" : "Confirm reservation"}</button>
            <p className="field-hint">The demo payment service authorizes the booking without collecting payment details.</p>
          </form>
        </div>
        <BookingSummary stay={props.stay} room={props.room} search={props.search} />
      </div>
    </section>
  );
}

function ConfirmationPage(props: ConfirmationPageProps) {
  const booking = props.booking;
  const stay: SearchStay = {
    id: booking.hotelId,
    name: booking.hotelName,
    city: booking.city,
    district: booking.district,
    address: "Lisbon, Portugal",
    summary: "",
    imagePath: booking.imagePath,
    imageAlt: booking.imageAlt,
    rating: 0,
    rooms: [],
  };
  const room: RoomOffer = {
    id: booking.roomTypeId,
    name: booking.roomTypeName,
    details: "",
    maxGuests: booking.guests,
    availableRooms: 0,
    nightlyRates: [],
    totalPrice: booking.total,
  };
  const search: SearchFormValues = {
    ...props.search,
    checkIn: booking.checkIn,
    checkOut: booking.checkOut,
    guests: String(booking.guests),
  };
  return (
    <section className="view confirmation-layout" aria-labelledby="confirmation-title">
      <div>
        <span className="confirmation-status">Reservation confirmed</span>
        <h1 className="confirmation-title" id="confirmation-title" tabIndex={-1} ref={props.pageHeadingRef}>Your Lisbon stay is on the list.</h1>
        <p className="confirmation-copy">The room is reserved for {booking.guestName}. A demo payment authorization has been recorded.</p>
        <div className="reference-line"><span>Reservation reference</span><strong>{booking.id.slice(0, 8).toUpperCase()}</strong></div>
        <p className="confirmation-caveat">This is a local demonstration. Payment is simulated and no hotel is contacted.</p>
        <button className="button button-primary" type="button" onClick={props.onShowBookings}>View my bookings</button>
      </div>
      <BookingSummary stay={stay} room={room} search={search} totalOverride={booking.total} />
    </section>
  );
}

function BookingsPage(props: BookingsPageProps) {
  return (
    <section className="view" aria-labelledby="bookings-title">
      <div className="bookings-heading">
        <div><p className="eyebrow">Your stays</p><h1 id="bookings-title" tabIndex={-1} ref={props.pageHeadingRef}>My bookings</h1></div>
        {props.bookings.length ? <p className="result-count">{props.bookings.length} {props.bookings.length === 1 ? "reservation" : "reservations"}</p> : null}
      </div>
      <form className="history-form" onSubmit={(event) => { event.preventDefault(); void props.onLoadBookings(props.historyEmail); }}>
        <div className="field">
          <label htmlFor="history-email">Email address used for booking</label>
          <input id="history-email" className="input-control" type="email" autoComplete="email" value={props.historyEmail} onChange={(event) => props.onHistoryEmailChange(event.target.value)} required />
        </div>
        <button className="button button-secondary" type="submit" disabled={props.historyLoading}>{props.historyLoading ? "Loading…" : "Find bookings"}</button>
      </form>
      {props.historyError ? <p className="checkout-error" role="alert">{props.historyError}</p> : null}
      {props.cancelError ? <p className="checkout-error" role="alert">{props.cancelError}</p> : null}
      <BookingResults bookings={props.bookings} onCancel={props.onCancelBooking} onShowExplore={props.onShowExplore} />
    </section>
  );
}

function BookingResults({
  bookings,
  onCancel,
  onShowExplore,
}: {
  readonly bookings: Reservation[];
  readonly onCancel: (id: string) => void;
  readonly onShowExplore: () => void;
}) {
  if (bookings.length) {
    return <div className="booking-list">{bookings.map((booking) => <BookingCard key={booking.id} booking={booking} onCancel={onCancel} />)}</div>;
  }
  return (
    <div className="empty-bookings">
      <p className="eyebrow">A clear slate</p>
      <h2>No bookings found for this email.</h2>
      <p>Reservations made in this demo appear here after you search with the same email address.</p>
      <button className="button button-primary" type="button" onClick={onShowExplore}>Search Lisbon stays</button>
    </div>
  );
}

interface SearchFormProps {
  readonly values: SearchFormValues;
  readonly error: string;
  readonly onChange: (key: keyof SearchFormValues, value: string) => void;
  readonly onSubmit: (event: SubmitEvent<HTMLFormElement>) => void;
}

function SearchForm({ values, error, onChange, onSubmit }: SearchFormProps) {
  return (
    <form className="search-form" onSubmit={onSubmit} aria-label="Search hotel stays" noValidate>
      <div className="field destination-field">
        <label htmlFor="destination">Destination</label>
        <input className="input-control" id="destination" autoComplete="address-level2" value={values.destination} onChange={(event) => onChange("destination", event.target.value)} aria-describedby={error ? "search-validation" : undefined} aria-invalid={Boolean(error)} required />
      </div>
      <div className="field">
        <label htmlFor="check-in">Check-in</label>
        <input className="input-control" id="check-in" type="date" min={localDateOffset(0)} value={values.checkIn} onChange={(event) => onChange("checkIn", event.target.value)} aria-invalid={Boolean(error)} required />
      </div>
      <div className="field">
        <label htmlFor="check-out">Check-out</label>
        <input className="input-control" id="check-out" type="date" min={addDays(values.checkIn, 1)} value={values.checkOut} onChange={(event) => onChange("checkOut", event.target.value)} aria-invalid={Boolean(error)} required />
      </div>
      <div className="field">
        <label htmlFor="guests">Guests</label>
        <select className="input-control" id="guests" value={values.guests} onChange={(event) => onChange("guests", event.target.value)}>
          {[1, 2, 3, 4].map((count) => <option value={count} key={count}>{count} {count === 1 ? "adult" : "adults"}</option>)}
        </select>
      </div>
      <button className="button button-primary" type="submit">Search stays</button>
      {error ? <p className="search-error" id="search-validation" role="alert">{error}</p> : null}
    </form>
  );
}

function StayCard({ stay, onSelect }: { readonly stay: SearchStay; readonly onSelect: (stay: SearchStay) => void }) {
  const room = [...stay.rooms].sort((first, second) => minimumRate(first) - minimumRate(second))[0];
  const available = Math.max(...stay.rooms.map((offer) => offer.availableRooms));
  return (
    <article className="stay-card">
      <figure><img src={stay.imagePath} srcSet={getResponsiveImageSources(stay.imagePath)} sizes="(max-width: 520px) 100vw, (max-width: 1060px) 38vw, 20vw" width="1200" height="800" alt={stay.imageAlt} loading="lazy" /></figure>
      <div className="stay-content">
        <p className="stay-place">{stay.district} · {stay.city}</p>
        <h3>{stay.name}</h3>
        <div className="stay-score"><span className="score-chip">{stay.rating.toFixed(1)}</span><span>guest score / 10</span></div>
        <p className="availability">{available} rooms available for these dates</p>
        <div className="stay-card-bottom">
          <div><span className="price-caption">From</span><span className="price-line">{formatMoney(minimumRate(room))} <small>/ night</small></span></div>
          <button className="text-button stay-link" type="button" onClick={() => onSelect(stay)}>View rooms</button>
        </div>
      </div>
    </article>
  );
}

function BookingSummary({
  stay,
  room,
  search,
  onContinue,
  totalOverride,
}: {
  readonly stay: SearchStay;
  readonly room: RoomOffer;
  readonly search: SearchFormValues;
  readonly onContinue?: () => void;
  readonly totalOverride?: number;
}) {
  const nights = getNights(search.checkIn, search.checkOut);
  return (
    <aside className="booking-summary" aria-label="Stay price summary">
      <p className="eyebrow">Your stay</p>
      <h2 className="summary-hotel">{stay.name}</h2>
      <p className="summary-place">{stay.district} · {stay.city}</p>
      <dl className="summary-list">
        <div><dt>Room</dt><dd>{room.name}</dd></div>
        <div><dt>Dates</dt><dd>{formatDate(search.checkIn, { day: "numeric", month: "short" })} – {formatDate(search.checkOut)}</dd></div>
        <div><dt>Guests</dt><dd>{search.guests} adults</dd></div>
        <div><dt>Length</dt><dd>{nights} nights</dd></div>
      </dl>
      <hr className="summary-rule" />
      <dl className="summary-list">
        {(room.nightlyRates ?? []).map((rate) => <div key={rate.date}><dt>{formatDate(rate.date, { weekday: "short", day: "numeric", month: "short" })}</dt><dd>{formatMoney(rate.amount)}</dd></div>)}
      </dl>
      <hr className="summary-rule" />
      <div className="summary-price-row"><span>Total for {nights} nights</span><strong>{formatMoney(totalOverride ?? room.totalPrice)}</strong></div>
      <p className="summary-caption">Rates are shown in euros. Taxes and fees are not included in this demo.</p>
      <p className="summary-policy"><strong>Cancellation terms vary by property.</strong> Review the hotel policy before booking.</p>
      {onContinue ? <button className="button button-primary summary-action" type="button" onClick={onContinue}>Continue to guest details</button> : null}
      {onContinue ? <p className="summary-no-payment">No card details are requested.</p> : null}
    </aside>
  );
}

function LoadingResults() {
  return (
    <div aria-busy="true">
      <output className="status-message">Checking room availability and nightly prices…</output>
      <div className="loading-region" aria-hidden="true">
        {[1, 2].map((item) => <div className="loading-card" key={item}><div className="skeleton" /><div className="skeleton-copy"><div className="skeleton-line short" /><div className="skeleton-line" /><div className="skeleton-line short" /></div></div>)}
      </div>
    </div>
  );
}

function BookingCard({ booking, onCancel }: { readonly booking: Reservation; readonly onCancel: (id: string) => void }) {
  const nights = getNights(booking.checkIn, booking.checkOut);
  const cancelled = booking.status === "CANCELLED";
  return (
    <article className="booking-card">
      <img src={booking.imagePath} srcSet={getResponsiveImageSources(booking.imagePath)} sizes="(max-width: 520px) 100vw, 24vw" width="1200" height="800" alt={booking.imageAlt} loading="lazy" />
      <div>
        <p className="stay-place">{booking.district} · {booking.city}</p>
        <h2>{booking.hotelName}</h2>
        <p className="result-count">{booking.id.slice(0, 8).toUpperCase()} · {cancelled ? "Cancelled reservation" : "Confirmed reservation"}</p>
        <dl className="booking-meta">
          <div><dt>Dates</dt><dd>{formatDate(booking.checkIn)} – {formatDate(booking.checkOut)}</dd></div>
          <div><dt>Room</dt><dd>{booking.roomTypeName}</dd></div>
          <div><dt>Guests</dt><dd>{booking.guests} adults</dd></div>
          <div><dt>Length</dt><dd>{nights} nights</dd></div>
        </dl>
      </div>
      <div className="booking-card-actions">
        <p className="booking-total">{formatMoney(booking.total)} <span className="price-caption">total</span></p>
        <span className={`booking-state${cancelled ? " cancelled" : ""}`}>{cancelled ? "Cancelled" : "Confirmed"}</span>
        {!cancelled ? <button className="button button-danger" type="button" onClick={() => onCancel(booking.id)}>Cancel reservation</button> : null}
      </div>
    </article>
  );
}

function StaffPage({
  queries,
  commands,
  adminKey,
  onAdminKeyChange,
  headingRef,
}: {
  readonly queries: HotelQueries;
  readonly commands: HotelCommands;
  readonly adminKey: string;
  readonly onAdminKeyChange: (value: string) => void;
  readonly headingRef: RefObject<HTMLHeadingElement | null>;
}) {
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [hotelId, setHotelId] = useState("");
  const [roomTypeId, setRoomTypeId] = useState("");
  const [roomDraft, setRoomDraft] = useState<RoomTypeDraft>({ name: "", details: "", maxGuests: 2, totalInventory: 10 });
  const [baseRate, setBaseRate] = useState("180");
  const [isNew, setIsNew] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [removeTarget, setRemoveTarget] = useState<string | null>(null);

  const selectedHotel = hotels.find((hotel) => hotel.id === hotelId) ?? null;
  const selectedRoom = selectedHotel?.roomTypes.find((room) => room.id === roomTypeId) ?? null;

  useEffect(() => {
    let active = true;
    queries.listHotels()
      .then((result) => {
        if (!active) return;
        setHotels(result);
        setHotelId(result[0]?.id ?? "");
        setRoomTypeId(result[0]?.roomTypes[0]?.id ?? "");
      })
      .catch((loadError: unknown) => { if (active) setError(errorMessage(loadError)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [queries]);

  useEffect(() => {
    if (!selectedRoom || isNew) return;
    setRoomDraft({
      name: selectedRoom.name,
      details: selectedRoom.details,
      maxGuests: selectedRoom.maxGuests,
      totalInventory: selectedRoom.totalInventory,
    });
  }, [selectedRoom, isNew]);

  function startNewRoom() {
    setIsNew(true);
    setRoomTypeId("");
    setRoomDraft({ name: "", details: "", maxGuests: 2, totalInventory: 10 });
    setBaseRate("180");
    setMessage("");
    setError("");
  }

  async function saveRoom(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setMessage("");
    try {
      if (!adminKey.trim()) throw new Error("Enter the staff key before saving changes.");
      if (!selectedHotel) throw new Error("Choose a hotel first.");
      let roomId = roomTypeId;
      if (isNew) {
        const created = await commands.createRoomType(selectedHotel.id, roomDraft, adminKey);
        roomId = created.id;
        setRoomTypeId(roomId);
        setIsNew(false);
      } else if (roomId) {
        await commands.updateRoomType(roomId, roomDraft, adminKey);
      } else {
        throw new Error("Choose a room type or start a new one.");
      }
      if (isNew) {
        await commands.createRateSchedule(roomId, Number(baseRate), adminKey);
      }
      await commands.changeInventory({
        hotelId: selectedHotel.id,
        roomTypeId: roomId,
        totalInventory: roomDraft.totalInventory,
      }, adminKey);
      const refreshed = await queries.listHotels();
      setHotels(refreshed);
      setMessage(isNew ? "Room type, inventory, and nightly rates added." : "Room type and inventory updated.");
    } catch (saveError) {
      setError(errorMessage(saveError));
    } finally {
      setSaving(false);
    }
  }

  async function removeRoomType() {
    if (!removeTarget || !adminKey.trim()) return;
    setError("");
    try {
      await commands.removeRoomType(removeTarget, adminKey);
      const refreshed = await queries.listHotels();
      setHotels(refreshed);
      const nextHotel = refreshed.find((hotel) => hotel.id === hotelId) ?? refreshed[0];
      setRoomTypeId(nextHotel?.roomTypes[0]?.id ?? "");
      setIsNew(false);
      setRemoveTarget(null);
      setMessage("Room type removed from new searches.");
    } catch (removeError) {
      setError(errorMessage(removeError));
    }
  }

  function changeRoomDraft(key: keyof RoomTypeDraft, value: string) {
    setRoomDraft((previous) => ({ ...previous, [key]: key === "name" || key === "details" ? value : Number(value) }));
  }

  function changeHotel(value: string) {
    setHotelId(value);
    setIsNew(false);
    setRoomTypeId("");
  }

  function cancelNewRoom() {
    setIsNew(false);
    setRoomTypeId(selectedHotel?.roomTypes[0]?.id ?? "");
  }

  return (
    <section className="view" aria-labelledby="staff-title">
      <p className="eyebrow">Hotel management</p>
      <h1 className="staff-title" id="staff-title" tabIndex={-1} ref={headingRef}>Staff room management</h1>
      <p className="lede staff-lede">Update room details and future inventory. A staff key is required for every change.</p>
      <div className="admin-layout">
        <StaffEditor
          adminKey={adminKey}
          onAdminKeyChange={onAdminKeyChange}
          loading={loading}
          hotels={hotels}
          hotelId={hotelId}
          onHotelChange={changeHotel}
          selectedHotel={selectedHotel}
          roomTypeId={roomTypeId}
          onRoomTypeChange={setRoomTypeId}
          roomDraft={roomDraft}
          onRoomDraftChange={changeRoomDraft}
          baseRate={baseRate}
          onBaseRateChange={setBaseRate}
          isNew={isNew}
          saving={saving}
          message={message}
          error={error}
          selectedRoom={selectedRoom}
          onSave={saveRoom}
          onStartNew={startNewRoom}
          onCancelNew={cancelNewRoom}
          onRemove={() => setRemoveTarget(selectedRoom?.id ?? null)}
        />
        <aside className="booking-summary admin-note">
          <p className="eyebrow">Internal operations</p>
          <h2 className="summary-hotel">Inventory is date based</h2>
          <p className="detail-description">The service synchronizes room capacity across future dates. The reservation service allows up to 10% overbooking and protects concurrent bookings with PostgreSQL version checks.</p>
          <p className="summary-policy"><strong>Local demo only.</strong> Keep the staff key private and use it only for authorized hotel updates.</p>
        </aside>
      </div>
      <StaffRemovalDialog
        open={removeTarget !== null}
        onCancel={() => setRemoveTarget(null)}
        onConfirm={removeRoomType}
      />
    </section>
  );
}

interface StaffEditorProps {
  readonly adminKey: string;
  readonly onAdminKeyChange: (value: string) => void;
  readonly loading: boolean;
  readonly hotels: Hotel[];
  readonly hotelId: string;
  readonly onHotelChange: (value: string) => void;
  readonly selectedHotel: Hotel | null;
  readonly roomTypeId: string;
  readonly onRoomTypeChange: (value: string) => void;
  readonly roomDraft: RoomTypeDraft;
  readonly onRoomDraftChange: (key: keyof RoomTypeDraft, value: string) => void;
  readonly baseRate: string;
  readonly onBaseRateChange: (value: string) => void;
  readonly isNew: boolean;
  readonly saving: boolean;
  readonly message: string;
  readonly error: string;
  readonly selectedRoom: Hotel["roomTypes"][number] | null;
  readonly onSave: (event: SubmitEvent<HTMLFormElement>) => void;
  readonly onStartNew: () => void;
  readonly onCancelNew: () => void;
  readonly onRemove: () => void;
}
type StaffRoomFormProps = Pick<
  StaffEditorProps,
  | "roomDraft"
  | "onRoomDraftChange"
  | "isNew"
  | "baseRate"
  | "onBaseRateChange"
  | "error"
  | "message"
  | "onSave"
  | "saving"
  | "loading"
  | "selectedRoom"
  | "onRemove"
  | "onStartNew"
  | "onCancelNew"
>;
type StaffRoomActionsProps = Pick<
  StaffEditorProps,
  "saving" | "loading" | "isNew" | "selectedRoom" | "onRemove" | "onStartNew" | "onCancelNew"
>;

function StaffEditor(props: StaffEditorProps) {
  return (
    <section className="admin-card" aria-label="Room type editor">
      <div className="field">
        <label htmlFor="admin-key">Staff key</label>
        <input className="input-control" id="admin-key" type="password" autoComplete="off" value={props.adminKey} onChange={(event) => props.onAdminKeyChange(event.target.value)} />
      </div>
      {props.loading ? <output className="status-message">Loading hotel details…</output> : null}
      {!props.loading && props.hotels.length === 0 && props.error ? <p className="checkout-error" role="alert">{props.error}</p> : null}
      {!props.loading && props.hotels.length > 0 ? (
        <>
          <StaffHotelFields
            hotels={props.hotels}
            hotelId={props.hotelId}
            onHotelChange={props.onHotelChange}
            selectedHotel={props.selectedHotel}
            roomTypeId={props.roomTypeId}
            onRoomTypeChange={props.onRoomTypeChange}
            isNew={props.isNew}
          />
          <StaffRoomForm
            roomDraft={props.roomDraft}
            onRoomDraftChange={props.onRoomDraftChange}
            isNew={props.isNew}
            baseRate={props.baseRate}
            onBaseRateChange={props.onBaseRateChange}
            error={props.error}
            message={props.message}
            onSave={props.onSave}
            saving={props.saving}
            loading={props.loading}
            selectedRoom={props.selectedRoom}
            onRemove={props.onRemove}
            onStartNew={props.onStartNew}
            onCancelNew={props.onCancelNew}
          />
        </>
      ) : null}
      {!props.loading && props.hotels.length === 0 ? <p className="status-message">No hotels are available to manage.</p> : null}
    </section>
  );
}

interface StaffHotelFieldsProps {
  readonly hotels: Hotel[];
  readonly hotelId: string;
  readonly onHotelChange: (value: string) => void;
  readonly selectedHotel: Hotel | null;
  readonly roomTypeId: string;
  readonly onRoomTypeChange: (value: string) => void;
  readonly isNew: boolean;
}

function StaffHotelFields(props: StaffHotelFieldsProps) {
  return (
    <>
      <div className="field">
        <label htmlFor="staff-hotel">Hotel</label>
        <select className="input-control" id="staff-hotel" value={props.hotelId} onChange={(event) => props.onHotelChange(event.target.value)}>
          {props.hotels.map((hotel) => <option value={hotel.id} key={hotel.id}>{hotel.name}</option>)}
        </select>
      </div>
      {!props.isNew ? (
        <div className="field">
          <label htmlFor="staff-room">Room type</label>
          <select className="input-control" id="staff-room" value={props.roomTypeId} onChange={(event) => props.onRoomTypeChange(event.target.value)}>
            {props.selectedHotel?.roomTypes.map((room) => <option value={room.id} key={room.id}>{room.name}</option>)}
          </select>
        </div>
      ) : null}
    </>
  );
}

function StaffRoomForm(props: StaffRoomFormProps) {
  return (
    <form className="admin-form" onSubmit={props.onSave}>
      <div className="field"><label htmlFor="room-name">Room name</label><input className="input-control" id="room-name" value={props.roomDraft.name} onChange={(event) => props.onRoomDraftChange("name", event.target.value)} required /></div>
      <div className="field"><label htmlFor="room-details">Room details</label><input className="input-control" id="room-details" value={props.roomDraft.details} onChange={(event) => props.onRoomDraftChange("details", event.target.value)} required /></div>
      <div className="admin-fields-row">
        <div className="field"><label htmlFor="room-guests">Guest capacity</label><input className="input-control" id="room-guests" type="number" min="1" max="8" value={props.roomDraft.maxGuests} onChange={(event) => props.onRoomDraftChange("maxGuests", event.target.value)} required /></div>
        <div className="field"><label htmlFor="room-inventory">Rooms in inventory</label><input className="input-control" id="room-inventory" type="number" min="1" value={props.roomDraft.totalInventory} onChange={(event) => props.onRoomDraftChange("totalInventory", event.target.value)} required /></div>
      </div>
      {props.isNew ? <div className="field"><label htmlFor="room-base-rate">Starting nightly rate (€)</label><input className="input-control" id="room-base-rate" type="number" min="1" step="0.01" value={props.baseRate} onChange={(event) => props.onBaseRateChange(event.target.value)} required /></div> : null}
      {props.error ? <p className="checkout-error" role="alert">{props.error}</p> : null}
      {props.message ? <output className="admin-success">{props.message}</output> : null}
      <StaffRoomActions
        saving={props.saving}
        loading={props.loading}
        isNew={props.isNew}
        selectedRoom={props.selectedRoom}
        onRemove={props.onRemove}
        onStartNew={props.onStartNew}
        onCancelNew={props.onCancelNew}
      />
    </form>
  );
}

function StaffRoomActions(props: StaffRoomActionsProps) {
  return (
    <div className="admin-actions">
      <button className="button button-primary" type="submit" disabled={props.saving || props.loading}>{saveLabel(props.saving, props.isNew)}</button>
      {!props.isNew && props.selectedRoom ? <button className="button button-danger" type="button" onClick={props.onRemove}>Remove room type</button> : null}
      {!props.isNew ? <button className="text-button" type="button" onClick={props.onStartNew}>Add another room type</button> : <button className="text-button" type="button" onClick={props.onCancelNew}>Cancel</button>}
    </div>
  );
}

interface StaffRemovalDialogProps {
  readonly open: boolean;
  readonly onCancel: () => void;
  readonly onConfirm: () => void;
}

function StaffRemovalDialog({ open, onCancel, onConfirm }: StaffRemovalDialogProps) {
  return (
    <dialog open={open} className="staff-dialog" aria-labelledby="remove-room-title">
      {open ? <>
        <p className="eyebrow">Staff action</p>
        <h2 id="remove-room-title">Remove this room type?</h2>
        <p>This hides it from new searches. Existing reservations remain in history.</p>
        <div className="dialog-actions">
          <button className="button button-secondary" type="button" onClick={onCancel}>Keep room type</button>
          <button className="button button-danger" type="button" onClick={onConfirm}>Remove room type</button>
        </div>
      </> : null}
    </dialog>
  );
}
