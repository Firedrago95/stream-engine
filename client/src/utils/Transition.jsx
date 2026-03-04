import React, { useRef, useEffect, useContext } from 'react';
import { CSSTransition as ReactCSSTransition } from 'react-transition-group';

const TransitionContext = React.createContext({
  parent: {},
});

function useParent() {
  return useContext(TransitionContext);
}

function CSSTransition({
  show,
  enter = '',
  enterStart = '',
  enterEnd = '',
  leave = '',
  leaveStart = '',
  leaveEnd = '',
  appear,
  unmountOnExit,
  tag = 'div',
  children,
  ...rest
}) {
  const enterClasses = enter.split(' ').filter((s) => s.length);
  const enterStartClasses = enterStart.split(' ').filter((s) => s.length);
  const enterEndClasses = enterEnd.split(' ').filter((s) => s.length);
  const leaveClasses = leave.split(' ').filter((s) => s.length);
  const leaveStartClasses = leaveStart.split(' ').filter((s) => s.length);
  const leaveEndClasses = leaveEnd.split(' ').filter((s) => s.length);
  const nodeRef = useRef(null);
  const Component = tag;

  return (
      <ReactCSSTransition
          appear={appear}
          nodeRef={nodeRef}
          unmountOnExit={unmountOnExit}
          in={show}
          addEndListener={(done) => {
            nodeRef.current.addEventListener('transitionend', done, false);
          }}
          onEnter={() => {
            nodeRef.current.classList.add(...enterClasses, ...enterStartClasses);
          }}
          onEntering={() => {
            nodeRef.current.classList.remove(...enterStartClasses);
            nodeRef.current.classList.add(...enterEndClasses);
          }}
          onEntered={() => {
            nodeRef.current.classList.remove(...enterEndClasses, ...enterClasses);
          }}
          onExit={() => {
            nodeRef.current.classList.add(...leaveClasses, ...leaveStartClasses);
          }}
          onExiting={() => {
            nodeRef.current.classList.remove(...leaveStartClasses);
            nodeRef.current.classList.add(...leaveEndClasses);
          }}
          onExited={() => {
            nodeRef.current.classList.remove(...leaveEndClasses, ...leaveClasses);
          }}
      >
        <Component ref={nodeRef} {...rest}>
          {children}
        </Component>
      </ReactCSSTransition>
  );
}

function Transition({ show, appear, ...rest }) {
  const { parent } = useParent();
  const isInitialRender = useRef(true);
  const isChild = show === undefined;

  useEffect(() => {
    isInitialRender.current = false;
  }, []);

  if (!isChild && !show && isInitialRender.current && !appear) return null;

  return (
      <TransitionContext.Provider
          value={{
            parent: {
              show: isChild ? parent.show : show,
              appear: isChild ? parent.appear : appear,
              isInitialRender,
            },
          }}
      >
        <CSSTransition show={isChild ? parent.show : show} appear={isChild ? parent.appear : appear} {...rest} />
      </TransitionContext.Provider>
  );
}

export default Transition;
